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
    String userId  = ctx.get("userId");
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      ctx.response().setStatusCode(400).end("{\"error\":\"Request body is required\"}");
      return;
    }

    // Mark manual entries with source=MANUAL so the UI can distinguish them
    body.put("source",    "MANUAL");
    body.put("anomalous", false);

    // Wrap in array — bulkInsert handles both batch and single entries
    JsonArray single = new JsonArray().add(body);

    transactionRepo.bulkInsert(userId, single)
      .onSuccess(v -> ctx.response()
        .setStatusCode(201)
        .end("{\"message\":\"Transaction created\"}"))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("{\"error\":\"Failed to create transaction\"}"));
  }
}
