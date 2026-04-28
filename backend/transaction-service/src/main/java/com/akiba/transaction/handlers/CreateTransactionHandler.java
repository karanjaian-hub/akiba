package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class CreateTransactionHandler {

  private final TransactionRepository transactionRepo;

  public CreateTransactionHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId = ctx.get("userId");

    if (userId == null || userId.isBlank()) {
      ctx.response()
        .setStatusCode(401)
        .putHeader("Content-Type", "application/json")
        .end("{\"error\":\"Unauthorized: missing user identity\"}");
      return;
    }

    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      ctx.response()
        .setStatusCode(400)
        .putHeader("Content-Type", "application/json")
        .end("{\"error\":\"Request body is required\"}");
      return;
    }

    // Log exactly what arrived so we can see it in docker-compose logs
    System.out.println("[CreateTransactionHandler] userId=" + userId + " body=" + body.encode());

    body.put("source",    "MANUAL");
    body.put("anomalous", false);

    JsonArray single = new JsonArray().add(body);

    transactionRepo.bulkInsert(userId, single)
      .onSuccess(v -> ctx.response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end("{\"message\":\"Transaction created\"}"))
      .onFailure(err -> {
        System.err.println("[CreateTransactionHandler] Failed: " + err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Failed to create transaction\"}");
      });
  }
}
