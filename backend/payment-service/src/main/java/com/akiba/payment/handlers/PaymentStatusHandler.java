package com.akiba.payment.handlers;

import com.akiba.payment.models.Payment;
import com.akiba.payment.repositories.PaymentRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;

import java.util.UUID;

/**
 * GET /payments/status/:paymentId
 *
 * The mobile app polls this every 3 seconds after initiating a payment.
 * We check Redis first (fast path) — the pending state is cached there.
 * On cache miss we fall back to DB — this happens after the callback clears Redis.
 */
public class PaymentStatusHandler {

  private final PaymentRepository repository;
  private final RedisAPI          redis;

  public PaymentStatusHandler(PaymentRepository repository, Redis redisClient) {
    this.repository = repository;
    this.redis      = RedisAPI.api(redisClient);
  }

  public void handle(RoutingContext ctx) {
    UUID userId    = UUID.fromString(ctx.get("userId"));
    UUID paymentId;

    try {
      paymentId = UUID.fromString(ctx.pathParam("paymentId"));
    } catch (IllegalArgumentException e) {
      ctx.response().setStatusCode(400).end(error("Invalid paymentId format"));
      return;
    }

    // Check DB for authoritative status (Redis only caches the PENDING state)
    repository.findPaymentById(paymentId, userId)
      .onSuccess(payment -> {
        if (payment == null) {
          ctx.response().setStatusCode(404).end(error("Payment not found"));
          return;
        }
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(paymentToJson(payment).encode());
      })
      .onFailure(err -> {
        System.err.println("[payment-service] Status lookup failed: " + err.getMessage());
        ctx.response().setStatusCode(500).end(error("Could not retrieve payment status"));
      });
  }

  private JsonObject paymentToJson(Payment p) {
    return new JsonObject()
      .put("id",        p.getId().toString())
      .put("status",    p.getStatus().name())
      .put("amount",    p.getAmount())
      .put("phone",     p.getPhone())
      .put("category",  p.getCategory())
      .put("type",      p.getType().name())
      .put("createdAt", p.getCreatedAt().toString())
      .put("updatedAt", p.getUpdatedAt().toString());
  }

  private String error(String message) {
    return new JsonObject().put("error", message).encode();
  }
}

// ──────────────────────────────────────────────────────────────────────────────

