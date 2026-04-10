package com.akiba.transaction.handlers;

import com.akiba.transaction.repositories.TransactionRepository;
import io.vertx.ext.web.RoutingContext;

public class GetTransactionsHandler {

  private final TransactionRepository transactionRepo;

  public GetTransactionsHandler(TransactionRepository transactionRepo) {
    this.transactionRepo = transactionRepo;
  }

  public void handle(RoutingContext ctx) {
    String userId   = ctx.get("userId");  // set by API Gateway JWT middleware
    int page        = parseIntParam(ctx, "page", 0);
    int size        = parseIntParam(ctx, "size", 20);
    String category = ctx.request().getParam("category");
    String dateFrom = ctx.request().getParam("dateFrom");
    String dateTo   = ctx.request().getParam("dateTo");
    String type     = ctx.request().getParam("type");
    String source   = ctx.request().getParam("source");

    transactionRepo.findAll(userId, page, size, category, dateFrom, dateTo, type, source)
      .onSuccess(transactions -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(transactions.encode()))
      .onFailure(err -> {
        System.err.println("[GetTransactionsHandler] " + err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .end("{\"error\":\"Failed to fetch transactions\"}");
      });
  }

  private int parseIntParam(RoutingContext ctx, String name, int defaultValue) {
    String raw = ctx.request().getParam(name);
    if (raw == null) return defaultValue;
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
