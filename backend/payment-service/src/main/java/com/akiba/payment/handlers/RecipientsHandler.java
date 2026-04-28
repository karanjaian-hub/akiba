package com.akiba.payment.handlers;

import com.akiba.payment.repositories.PaymentRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

/**
 * GET  /payments/recipients — returns saved recipients for the "recent recipients" chips
 * PUT  /payments/recipients/:id — update nickname or category
 */
public class RecipientsHandler {

  private final PaymentRepository repository;

  public RecipientsHandler(PaymentRepository repository) {
    this.repository = repository;
  }

  public void handleGet(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.get("userId"));

    repository.findRecipients(userId)
      .onSuccess(recipients -> {
        JsonArray arr = new JsonArray();
        recipients.forEach(r -> arr.add(new JsonObject()
          .put("id",              r.getId().toString())
          .put("identifier",      r.getIdentifier())
          .put("nickname",        r.getNickname())
          .put("defaultCategory", r.getDefaultCategory())
          .put("type",            r.getType().name())
          .put("useCount",        r.getUseCount())
          .put("lastUsedAt",      r.getLastUsedAt() != null ? r.getLastUsedAt().toString() : null)
        ));
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(arr.encode());
      })
      .onFailure(err -> {
        System.err.println("[payment-service] Get recipients failed: " + err.getMessage());
        ctx.response().setStatusCode(500)
          .end(new JsonObject().put("error", "Could not retrieve recipients").encode());
      });
  }

  public void handleUpdate(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.get("userId"));
    UUID recipientId;

    try {
      recipientId = UUID.fromString(ctx.pathParam("id"));
    } catch (IllegalArgumentException e) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "Invalid recipient ID").encode());
      return;
    }

    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "Request body is required").encode());
      return;
    }

    String nickname = body.getString("nickname");
    String category = body.getString("defaultCategory");

    repository.updateRecipient(recipientId, userId, nickname, category)
      .onSuccess(v -> ctx.response().setStatusCode(204).end()) // 204 No Content — update successful
      .onFailure(err -> {
        System.err.println("[payment-service] Update recipient failed: " + err.getMessage());
        ctx.response().setStatusCode(500)
          .end(new JsonObject().put("error", "Could not update recipient").encode());
      });
  }
}
