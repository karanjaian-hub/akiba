package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.ext.web.RoutingContext;

public class GetSummaryHandler {

  private final TransactionRepository transactionRepo;

  public GetSummaryHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId = ctx.get("userId");

    transactionRepo.getSummary(userId)
      .onSuccess(summary -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(summary.encode()))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("{\"error\":\"Failed to fetch summary\"}"));
  }
}
