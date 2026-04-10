package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.ext.web.RoutingContext;

public class GetTransactionByIdHandler {

  private final TransactionRepository transactionRepo;

  public GetTransactionByIdHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId        = ctx.get("userId");
    String transactionId = ctx.pathParam("id");

    transactionRepo.findById(userId, transactionId)
      .onSuccess(tx -> {
        if (tx == null) {
          ctx.response().setStatusCode(404).end("{\"error\":\"Transaction not found\"}");
          return;
        }
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(tx.encode());
      })
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("{\"error\":\"Failed to fetch transaction\"}"));
  }
}
