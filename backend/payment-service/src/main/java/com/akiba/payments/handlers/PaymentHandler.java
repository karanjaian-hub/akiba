package com.akiba.payments.handlers;

import com.akiba.payments.repositories.PaymentRepository;
import com.akiba.payments.services.DarajaService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One method per HTTP endpoint. Each method follows the same pattern:
 *   1. Extract & validate inputs
 *   2. Call service/repository
 *   3. Reply with JSON
 *
 * The handler doesn't know about SQL or Daraja internals — it just orchestrates.
 */
public class PaymentHandler {

  private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

  // Redis key prefix for pending payments — TTL of 120s matches M-Pesa session timeout
  private static final String PENDING_KEY_PREFIX = "payment:";
  private static final int    PENDING_TTL_SECONDS = 120;

  // RabbitMQ queue that budget-service and notification-service listen on
  private static final String PAYMENT_COMPLETED_QUEUE = "payment.completed";

  private final DarajaService     darajaService;
  private final PaymentRepository paymentRepository;
  private final WebClient         webClient;
  private final RedisAPI          redis;
  private final RabbitMQClient    rabbitMQ;
  private final String            budgetServiceUrl;

  public PaymentHandler(
      DarajaService darajaService,
      PaymentRepository paymentRepository,
      WebClient webClient,
      RedisAPI redis,
      RabbitMQClient rabbitMQ,
      String budgetServiceUrl) {
    this.darajaService     = darajaService;
    this.paymentRepository = paymentRepository;
    this.webClient         = webClient;
    this.redis             = redis;
    this.rabbitMQ          = rabbitMQ;
    this.budgetServiceUrl  = budgetServiceUrl;
  }

  // -------------------------------------------------------------------------
  // POST /payments/initiate
  // -------------------------------------------------------------------------

  /**
   * Full initiate flow:
   *   1. Validate request body
   *   2. Ask budget-service if user can afford this
   *   3. Fire STK Push to Daraja
   *   4. Save payment as PENDING in DB + Redis
   *   5. Upsert recipient for quick re-use
   *   6. Return immediately (mobile polls /status for result)
   */
  public void initiatePayment(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      replyError(ctx, 400, "Request body is required");
      return;
    }

    String userId   = ctx.user().subject(); // extracted from JWT by API gateway
    String phone    = body.getString("phone");
    Double amount   = body.getDouble("amount");
    String category = body.getString("category");

    if (phone == null || amount == null || category == null) {
      replyError(ctx, 400, "phone, amount, and category are required");
      return;
    }

    checkBudgetAffordability(userId, category, amount)
      .compose(canAfford -> {
        if (!canAfford) {
          // Return 400 immediately — don't even hit Daraja
          replyError(ctx, 400, "Budget limit reached for category: " + category);
          return Future.failedFuture("budget_exceeded"); // stops the chain
        }
        return fireStkPush(body, amount);
      })
      .compose(darajaResponse -> saveAndRespond(ctx, userId, body, amount, darajaResponse))
      .onFailure(err -> {
        // "budget_exceeded" was already responded to above, so swallow it here
        if (!"budget_exceeded".equals(err.getMessage())) {
          log.error("Payment initiation failed", err);
          replyError(ctx, 502, "Payment initiation failed: " + err.getMessage());
        }
      });
  }

  // -------------------------------------------------------------------------
  // POST /payments/callback  (Daraja webhook — no JWT)
  // -------------------------------------------------------------------------

  /**
   * Daraja calls this after the customer completes or cancels the M-Pesa prompt.
   * We must reply with 200 quickly or Daraja will retry.
   */
  public void handleCallback(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      ctx.response().setStatusCode(200).end(); // always ACK Daraja
      return;
    }

    JsonObject stk = body.getJsonObject("Body", new JsonObject())
      .getJsonObject("stkCallback", new JsonObject());

    String checkoutId   = stk.getString("CheckoutRequestID");
    int    resultCode   = stk.getInteger("ResultCode", -1);
    String status       = resultCode == 0 ? "COMPLETED" : "FAILED";
    String mpesaReceipt = extractMpesaReceipt(stk);

    // ACK Daraja immediately — don't wait for our DB write
    ctx.response().setStatusCode(200).end();

    // Update DB, clear Redis, publish to RabbitMQ (all async, failures logged not thrown)
    paymentRepository.updatePaymentStatus(checkoutId, status, mpesaReceipt)
      .compose(v -> clearPendingCache(checkoutId))
      .compose(v -> fetchAndPublishEvent(checkoutId, status))
      .onFailure(err -> log.error("Callback post-processing failed for {}", checkoutId, err));
  }

  // -------------------------------------------------------------------------
  // GET /payments/history
  // -------------------------------------------------------------------------

  public void getPaymentHistory(RoutingContext ctx) {
    String userId = ctx.user().subject();
    int page = intParam(ctx, "page", 0);
    int size = intParam(ctx, "size", 20);

    paymentRepository.getPaymentHistory(userId, page, size)
      .onSuccess(payments -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("payments", payments).encode()))
      .onFailure(err -> {
        log.error("Failed to fetch payment history for user {}", userId, err);
        replyError(ctx, 500, "Could not retrieve payment history");
      });
  }

  // -------------------------------------------------------------------------
  // GET /payments/status/:paymentId
  // -------------------------------------------------------------------------

  /**
   * The mobile app polls this every 3 seconds while showing the "pending" spinner.
   * We check Redis first (fast path) then fall back to DB.
   */
  public void getPaymentStatus(RoutingContext ctx) {
    String paymentId = ctx.pathParam("paymentId");
    String userId    = ctx.user().subject();

    // Redis holds PENDING state for 120s — if it's gone, payment resolved
    redis.get(PENDING_KEY_PREFIX + paymentId)
      .compose(cached -> {
        if (cached != null) {
          // Still pending — return quickly without hitting DB
          return Future.succeededFuture(new JsonObject().put("status", "PENDING").put("id", paymentId));
        }
        return paymentRepository.getPaymentStatus(paymentId, userId);
      })
      .onSuccess(result -> {
        if (result == null) {
          replyError(ctx, 404, "Payment not found");
          return;
        }
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .end(result.encode());
      })
      .onFailure(err -> {
        log.error("Status check failed for payment {}", paymentId, err);
        replyError(ctx, 500, "Could not retrieve payment status");
      });
  }

  // -------------------------------------------------------------------------
  // GET /payments/recipients
  // -------------------------------------------------------------------------

  public void getRecipients(RoutingContext ctx) {
    String userId = ctx.user().subject();

    paymentRepository.getRecipients(userId)
      .onSuccess(recipients -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("recipients", recipients).encode()))
      .onFailure(err -> {
        log.error("Failed to fetch recipients for user {}", userId, err);
        replyError(ctx, 500, "Could not retrieve recipients");
      });
  }

  // -------------------------------------------------------------------------
  // PUT /payments/recipients/:id
  // -------------------------------------------------------------------------

  public void updateRecipient(RoutingContext ctx) {
    String recipientId = ctx.pathParam("id");
    String userId      = ctx.user().subject();
    JsonObject updates = ctx.body().asJsonObject();

    if (updates == null || (updates.getString("nickname") == null && updates.getString("defaultCategory") == null)) {
      replyError(ctx, 400, "Provide nickname or defaultCategory to update");
      return;
    }

    paymentRepository.updateRecipient(recipientId, userId, updates)
      .onSuccess(v -> ctx.response().setStatusCode(204).end())
      .onFailure(err -> {
        log.error("Failed to update recipient {}", recipientId, err);
        replyError(ctx, 500, "Could not update recipient");
      });
  }

  // -------------------------------------------------------------------------
  // GET /health
  // -------------------------------------------------------------------------

  public void healthCheck(RoutingContext ctx) {
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("status", "UP").put("service", "payment-service").encode());
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /** Calls budget-service to check if this spend is within the user's budget. */
  private Future<Boolean> checkBudgetAffordability(String userId, String category, double amount) {
    String url = budgetServiceUrl + "/budgets/" + category + "/check?amount=" + (int) amount;

    return webClient.getAbs(url)
      .putHeader("X-User-Id", userId)  // budget-service reads user from this header (internal call)
      .send()
      .map(response -> {
        if (response.statusCode() != 200) return true; // fail open — don't block payment on budget service errors
        return response.bodyAsJsonObject().getBoolean("canAfford", true);
      })
      .recover(err -> {
        // If budget service is down, let the payment proceed — money > budgets
        log.warn("Budget check failed, allowing payment: {}", err.getMessage());
        return Future.succeededFuture(true);
      });
  }

  private Future<JsonObject> fireStkPush(JsonObject body, double amount) {
    String phone       = body.getString("phone");
    String accountRef  = body.getString("accountReference", "Akiba");
    String description = body.getString("description", "Akiba Payment");

    // M-Pesa only accepts whole numbers for amount
    return darajaService.initiateStkPush(phone, (int) amount, accountRef, description);
  }

  private Future<Void> saveAndRespond(
      RoutingContext ctx, String userId, JsonObject body, double amount, JsonObject darajaResponse) {

    String checkoutId = darajaResponse.getString("CheckoutRequestID");

    JsonObject payment = new JsonObject()
      .put("userId", userId)
      .put("phone", body.getString("phone"))
      .put("amount", amount)
      .put("recipientType", body.getString("recipient_type"))
      .put("recipientIdentifier", body.getString("recipient_identifier"))
      .put("category", body.getString("category"))
      .put("description", body.getString("description"))
      .put("accountReference", body.getString("account_reference"))
      .put("checkoutId", checkoutId);

    return paymentRepository.createPayment(payment)
      .compose(paymentId -> {
        // Cache pending state in Redis — the poll endpoint reads this
        return redis.setex(PENDING_KEY_PREFIX + paymentId, String.valueOf(PENDING_TTL_SECONDS), "PENDING")
          .map(paymentId);
      })
      .compose(paymentId -> {
        // Fire-and-forget: upsert recipient (don't fail the payment if this fails)
        upsertRecipientSilently(userId, body);

        // Respond immediately — mobile app will poll for the outcome
        ctx.response()
          .setStatusCode(202)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject()
            .put("paymentId", paymentId)
            .put("status", "PENDING")
            .put("message", "Check your phone for M-Pesa prompt")
            .encode());

        return Future.succeededFuture();
      });
  }

  private void upsertRecipientSilently(String userId, JsonObject body) {
    JsonObject recipient = new JsonObject()
      .put("userId", userId)
      .put("identifier", body.getString("recipient_identifier"))
      .put("recipientType", body.getString("recipient_type"))
      .put("nickname", body.getString("nickname"))
      .put("category", body.getString("category"));

    paymentRepository.upsertRecipient(recipient)
      .onFailure(err -> log.warn("Failed to upsert recipient (non-critical): {}", err.getMessage()));
  }

  private Future<Void> clearPendingCache(String checkoutId) {
    // We need the paymentId to clear its Redis key — look it up via checkoutId
    // For simplicity, we store both: "checkout:{checkoutId}" → paymentId when we save
    // TODO: store checkout→payment mapping in Redis during saveAndRespond
    return Future.succeededFuture(); // placeholder — see TODO above
  }

  private Future<Void> fetchAndPublishEvent(String checkoutId, String status) {
    // Build RabbitMQ event for budget-service and notification-service
    JsonObject event = new JsonObject()
      .put("checkoutId", checkoutId)
      .put("status", status)
      .put("timestamp", System.currentTimeMillis());

    return rabbitMQ.basicPublish("", PAYMENT_COMPLETED_QUEUE,
        new io.vertx.rabbitmq.RabbitMQMessage() {
          @Override public io.vertx.core.buffer.Buffer body() {
            return io.vertx.core.buffer.Buffer.buffer(event.encode());
          }
          // Other interface methods return null (not used for basic publish)
          @Override public com.rabbitmq.client.Envelope envelope() { return null; }
          @Override public com.rabbitmq.client.AMQP.BasicProperties properties() { return null; }
          @Override public String consumerTag() { return null; }
        })
      .mapEmpty();
  }

  /** Extracts the M-Pesa receipt number from the callback metadata array. */
  private String extractMpesaReceipt(JsonObject stk) {
    var metadata = stk.getJsonObject("CallbackMetadata");
    if (metadata == null) return null;

    for (Object item : metadata.getJsonArray("Item", new io.vertx.core.json.JsonArray())) {
      JsonObject entry = (JsonObject) item;
      if ("MpesaReceiptNumber".equals(entry.getString("Name"))) {
        return entry.getString("Value");
      }
    }
    return null;
  }

  private int intParam(RoutingContext ctx, String param, int defaultValue) {
    String value = ctx.queryParam(param).stream().findFirst().orElse(null);
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private void replyError(RoutingContext ctx, int status, String message) {
    ctx.response()
      .setStatusCode(status)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
