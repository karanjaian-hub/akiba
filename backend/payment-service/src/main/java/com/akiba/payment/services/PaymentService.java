package com.akiba.payment.services;

import com.akiba.payment.config.PaymentConfig;
import com.akiba.payment.models.Payment;
import com.akiba.payment.repositories.PaymentRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PaymentService sits between the HTTP handlers and the infrastructure (Daraja, DB, Redis, RabbitMQ).
 *
 * It owns the two-step payment flow:
 *   1. Budget check  → is there budget room for this payment?
 *   2. STK Push      → ask Daraja to prompt the user's phone
 *   3. Persist       → save the PENDING record so we can match the callback later
 *   4. Cache         → store pending state in Redis for fast status polling
 *
 * The callback (step 5) is handled separately in DarajaCallbackHandler.
 */
public class PaymentService {

  // Redis TTL for pending payment state — Daraja callbacks arrive within ~30s in practice,
  // but we give 120s as a safety buffer before treating the payment as timed-out.
  private static final int PENDING_PAYMENT_TTL_SEC = 120;

  private final DarajaService     daraja;
  private final PaymentRepository repository;
  private final RedisAPI          redis;
  private final WebClient         webClient;
  private final RabbitMQClient    rabbitMQ;

  public PaymentService(Vertx vertx, PaymentRepository repository,
                        DarajaService daraja, Redis redisClient, RabbitMQClient rabbitMQ) {
    this.daraja     = daraja;
    this.repository = repository;
    this.redis      = RedisAPI.api(redisClient);
    this.webClient  = WebClient.create(vertx);
    this.rabbitMQ   = rabbitMQ;
  }

  // ── Initiate payment ───────────────────────────────────────────────────────

  /**
   * Orchestrates the full initiation flow.
   * Returns early with a 400 error if the budget would be exceeded.
   * Returns a PENDING payment record if the STK push succeeded.
   */
  public Future<Payment> initiatePayment(UUID userId, String phone, BigDecimal amount,
                                         String category, Payment.Type type, String accountRef) {
    return checkBudget(userId, category, amount)
      .compose(canAfford -> {
        if (!canAfford) {
          return Future.failedFuture("BUDGET_EXCEEDED: Not enough budget in " + category);
        }
        return triggerStkAndSave(userId, phone, amount, category, type, accountRef);
      });
  }

  /**
   * Calls the budget-service to check whether this payment would exceed the monthly limit.
   * Returns false if over budget, true if fine.
   *
   * WHY a dedicated budget-service call: budget logic is the budget-service's responsibility.
   * We ask, it answers — separation of concerns.
   */
  private Future<Boolean> checkBudget(UUID userId, String category, BigDecimal amount) {
    // PLACEHOLDER: replace with real JWT propagation when auth middleware is in place.
    // The budget-service endpoint expects the calling user's JWT so it can scope the check.
    String budgetUrl = PaymentConfig.budgetServiceUrl()
      + "/budgets/" + category + "/check?amount=" + amount;

    return webClient.getAbs(budgetUrl)
      .putHeader("X-User-Id", userId.toString()) // internal header trusted within the cluster
      .send()
      .map(resp -> {
        if (resp.statusCode() != 200) {
          // If budget-service is down, fail open (allow the payment) to avoid blocking users.
          // Log the degraded state so ops can investigate.
          System.err.println("[payment-service] Budget check failed (budget-service returned "
            + resp.statusCode() + ") — failing open for user " + userId);
          return true;
        }
        return resp.bodyAsJsonObject().getBoolean("canAfford", true);
      })
      .recover(err -> {
        System.err.println("[payment-service] Budget-service unreachable — failing open: " + err.getMessage());
        return Future.succeededFuture(true); // fail open
      });
  }

  /**
   * Sends the STK Push to Daraja, then saves the PENDING record to DB and Redis.
   * All three steps are chained with Future.compose() so any failure stops the chain.
   */
  private Future<Payment> triggerStkAndSave(UUID userId, String phone, BigDecimal amount,
                                            String category, Payment.Type type, String accountRef) {
    String description = "Akiba payment to " + (accountRef != null ? accountRef : phone);

    return daraja.initiateStkPush(phone, amount.intValue(), accountRef != null ? accountRef : phone, description)
      .compose(darajaResponse -> {
        Payment payment = buildPendingPayment(
          userId, phone, amount, category, type, accountRef, darajaResponse
        );
        return repository.insertPayment(payment);
      })
      .compose(savedPayment -> {
        // Cache the pending state so the mobile app can poll status cheaply
        // without hammering the DB every 3 seconds.
        String cacheKey = "payment:" + savedPayment.getCheckoutRequestId();
        JsonObject cached = new JsonObject()
          .put("paymentId", savedPayment.getId().toString())
          .put("status",    "PENDING");

        redis.setex(cacheKey, String.valueOf(PENDING_PAYMENT_TTL_SEC), cached.encode());
        return Future.succeededFuture(savedPayment);
      });
  }

  private Payment buildPendingPayment(UUID userId, String phone, BigDecimal amount,
                                      String category, Payment.Type type, String accountRef,
                                      JsonObject darajaResponse) {
    Payment p = new Payment();
    p.setUserId(userId);
    p.setPhone(phone);
    p.setAccountRef(accountRef);
    p.setAmount(amount);
    p.setCategory(category);
    p.setType(type);
    p.setStatus(Payment.Status.PENDING);
    p.setCheckoutRequestId(darajaResponse.getString("CheckoutRequestID"));
    p.setMerchantRequestId(darajaResponse.getString("MerchantRequestID"));
    return p;
  }

  // ── Callback processing ────────────────────────────────────────────────────

  /**
   * Called by DarajaCallbackHandler when Safaricom posts the payment result.
   * Updates DB status, clears Redis pending state, and — on success — publishes
   * the completed event to RabbitMQ so budget-service and notifications can react.
   */
  public Future<Void> processCallback(String checkoutRequestId, String resultCode,
                                      String resultDesc) {
    boolean success = "0".equals(resultCode);
    Payment.Status newStatus = success ? Payment.Status.COMPLETED : Payment.Status.FAILED;

    return repository.findByCheckoutRequestId(checkoutRequestId)
      .compose(payment -> {
        if (payment == null) {
          System.err.println("[payment-service] Callback received for unknown checkoutRequestId: "
            + checkoutRequestId);
          return Future.succeededFuture(); // ack anyway — Daraja will retry if we NACK
        }
        return repository.updatePaymentStatus(checkoutRequestId, newStatus, resultCode, resultDesc)
          .compose(v -> clearPendingCache(checkoutRequestId))
          .compose(v -> success ? publishPaymentCompleted(payment) : Future.succeededFuture());
      });
  }

  private Future<Void> clearPendingCache(String checkoutRequestId) {
    return redis.del(java.util.List.of("payment:" + checkoutRequestId)).mapEmpty();
  }

  /**
   * Publishes a 'payment.completed' event to RabbitMQ.
   * The budget-service and notification-service listen on this queue.
   *
   * WHY RabbitMQ: we don't want to call those services directly — if one is down,
   * we'd lose the event. RabbitMQ persists it until the consumer is ready.
   *
   * API: basicPublish(exchange, routingKey, Buffer)
   * - exchange ""  = default exchange; routingKey = queue name directly
   * - body is a plain Buffer — no wrapper object, no options class
   */
  private Future<Void> publishPaymentCompleted(Payment payment) {
    JsonObject event = new JsonObject()
      .put("userId",    payment.getUserId().toString())
      .put("paymentId", payment.getId().toString())
      .put("amount",    payment.getAmount())
      .put("category",  payment.getCategory())
      .put("recipient", payment.getPhone())
      .put("type",      payment.getType().name())
      .put("timestamp", java.time.Instant.now().toString());

    Buffer body = Buffer.buffer(event.encode());

    return rabbitMQ.basicPublish("", "payment.completed", body);
  }
}
