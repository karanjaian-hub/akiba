package com.akiba.payment.handlers;

import com.akiba.payment.models.Payment;
import com.akiba.payment.repositories.PaymentRepository;
import com.akiba.payment.services.PaymentService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles POST /payments/initiate
 *
 * Request JSON:
 * {
 *   "phone":      "254712345678",
 *   "amount":     1500,
 *   "category":   "Bills",
 *   "type":       "PHONE" | "TILL" | "PAYBILL",
 *   "accountRef": "KPLC-12345",   // required for PAYBILL, optional otherwise
 *   "nickname":   "KPLC Token"    // optional — saved to recipients table
 * }
 *
 * Response (202 Accepted):
 * { "paymentId": "...", "status": "PENDING", "message": "Check your phone for M-Pesa prompt" }
 *
 * Response (400 Bad Request) on budget exceeded:
 * { "error": "BUDGET_EXCEEDED", "message": "Not enough budget in Bills" }
 */
public class InitiatePaymentHandler {

  private final PaymentService     paymentService;
  private final PaymentRepository  repository;

  public InitiatePaymentHandler(PaymentService paymentService, PaymentRepository repository) {
    this.paymentService = paymentService;
    this.repository     = repository;
  }

  public void handle(RoutingContext ctx) {
    // userId comes from the JWT middleware — already validated upstream in the API Gateway
    UUID userId = UUID.fromString(ctx.get("userId"));

    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      ctx.response().setStatusCode(400).end(error("Request body is required"));
      return;
    }

    String phoneRaw   = body.getString("phone");
    Number amountRaw  = body.getNumber("amount");
    String category   = body.getString("category", "Other");
    String typeRaw    = body.getString("type");
    String accountRef = body.getString("accountRef");
    String nickname   = body.getString("nickname");

    // ── Input validation — fail early with descriptive errors ──────────────
    if (phoneRaw == null || phoneRaw.isBlank()) {
      ctx.response().setStatusCode(400).end(error("'phone' is required"));
      return;
    }
    if (amountRaw == null || amountRaw.doubleValue() <= 0) {
      ctx.response().setStatusCode(400).end(error("'amount' must be a positive number"));
      return;
    }
    if (typeRaw == null) {
      ctx.response().setStatusCode(400).end(error("'type' is required: PHONE, TILL, or PAYBILL"));
      return;
    }

    Payment.Type paymentType;
    try {
      paymentType = Payment.Type.valueOf(typeRaw.toUpperCase());
    } catch (IllegalArgumentException e) {
      ctx.response().setStatusCode(400).end(error("Invalid type. Use: PHONE, TILL, or PAYBILL"));
      return;
    }

    // Normalize phone to Safaricom format: 254XXXXXXXXX
    String phone     = normalizePhone(phoneRaw);
    BigDecimal amount = BigDecimal.valueOf(amountRaw.longValue());

    // ── Orchestrate the payment flow ───────────────────────────────────────
    paymentService.initiatePayment(userId, phone, amount, category, paymentType, accountRef)
      .onSuccess(payment -> {
        // Upsert the recipient in the background — don't block the payment response on this
        repository.upsertRecipient(userId, phone, nickname, category, paymentType.name())
          .onFailure(err -> System.err.println(
            "[payment-service] Recipient upsert failed (non-critical): " + err.getMessage()
          ));

        JsonObject response = new JsonObject()
          .put("paymentId", payment.getId().toString())
          .put("status",    "PENDING")
          .put("message",   "Check your phone for M-Pesa prompt");

        ctx.response()
          .setStatusCode(202) // 202 Accepted — result comes via callback
          .putHeader("Content-Type", "application/json")
          .end(response.encode());
      })
      .onFailure(err -> {
        String msg = err.getMessage();

        // Budget exceeded is a user-facing 400, not a server error
        if (msg != null && msg.startsWith("BUDGET_EXCEEDED")) {
          ctx.response().setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
              .put("error",   "BUDGET_EXCEEDED")
              .put("message", msg.replace("BUDGET_EXCEEDED: ", ""))
              .encode());
          return;
        }

        System.err.println("[payment-service] Payment initiation failed: " + msg);
        ctx.response().setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(error("Payment initiation failed. Please try again."));
      });
  }

  /**
   * Converts Kenyan phone formats to Daraja-required 254XXXXXXXXX format.
   * e.g. 0712345678 → 254712345678
   *      +254712345678 → 254712345678
   */
  private String normalizePhone(String phone) {
    phone = phone.replaceAll("\\s+", "").replaceAll("-", "");
    if (phone.startsWith("+254")) return phone.substring(1);
    if (phone.startsWith("0"))   return "254" + phone.substring(1);
    if (phone.startsWith("254")) return phone;
    return "254" + phone; // assume local format
  }

  private String error(String message) {
    return new JsonObject().put("error", message).encode();
  }
}
