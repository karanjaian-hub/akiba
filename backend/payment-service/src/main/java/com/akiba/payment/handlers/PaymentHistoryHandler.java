package com.akiba.payment.handlers;

import com.akiba.payment.repositories.PaymentRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.UUID;

/**
 * GET /payments/history?page=0&size=20
 *
 * Returns paginated payment history for the authenticated user, newest first.
 * page is 0-based: page=0 → first page, page=1 → second page.
 * size is capped at 50 server-side regardless of what the client sends.
 */
public class PaymentHistoryHandler {

  private final PaymentRepository repository;

  public PaymentHistoryHandler(PaymentRepository repository) {
    this.repository = repository;
  }

  public void handle(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.get("userId"));

    int page = parseIntParam(ctx.queryParam("page"), 0);
    int size = Math.min(parseIntParam(ctx.queryParam("size"), 20), 50);

    // OFFSET = page * size — page 0 returns the first 'size' records
    int offset = Math.max(page, 0) * size;

    repository.findPaymentHistory(userId, offset, size)
      .onSuccess(payments -> {
        JsonArray arr = new JsonArray();
        payments.forEach(p -> arr.add(new JsonObject()
          .put("id",        p.getId().toString())
          .put("status",    p.getStatus().name())
          .put("amount",    p.getAmount())
          .put("phone",     p.getPhone())
          .put("category",  p.getCategory())
          .put("type",      p.getType().name())
          .put("createdAt", p.getCreatedAt().toString())
        ));

        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject()
            .put("page", page)
            .put("size", size)
            .put("data", arr)
            .encode());
      })
      .onFailure(err -> {
        System.err.println("[payment-service] History fetch failed: " + err.getMessage());
        ctx.response().setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "Could not retrieve payment history").encode());
      });
  }

  private int parseIntParam(List<String> values, int defaultVal) {
    if (values == null || values.isEmpty()) return defaultVal;
    try { return Integer.parseInt(values.get(0)); }
    catch (NumberFormatException e) { return defaultVal; }
  }
}
