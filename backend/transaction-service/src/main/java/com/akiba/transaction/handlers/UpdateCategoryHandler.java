package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class UpdateCategoryHandler {

  private final TransactionRepository transactionRepo;

  public UpdateCategoryHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId        = ctx.get("userId");
    String transactionId = ctx.pathParam("id");
    JsonObject body      = ctx.body().asJsonObject();

    if (body == null || !body.containsKey("category")) {
      ctx.response().setStatusCode(400).end("{\"error\":\"category field is required\"}");
      return;
    }

    String newCategory = body.getString("category");

    transactionRepo.updateCategory(userId, transactionId, newCategory)
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .end("{\"message\":\"Category updated\"}"))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("{\"error\":\"Failed to update category\"}"));
  }
}
