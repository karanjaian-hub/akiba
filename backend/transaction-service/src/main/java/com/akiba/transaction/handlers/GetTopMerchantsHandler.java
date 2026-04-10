package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.ext.web.RoutingContext;

public class GetTopMerchantsHandler {

  private final TransactionRepository transactionRepo;

  public GetTopMerchantsHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId = ctx.get("userId");

    transactionRepo.getTopMerchants(userId)
      .onSuccess(merchants -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(merchants.encode()))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("{\"error\":\"Failed to fetch top merchants\"}"));
  }
}
