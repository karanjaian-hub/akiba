package com.akiba.budget.handlers;

import com.akiba.budget.repositories.BudgetRepository;
import com.akiba.budget.services.BudgetCacheService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.LocalDateTime;

public class BudgetCheckHandler {

    private final BudgetRepository   budgetRepo;
    private final BudgetCacheService cacheService;

    public BudgetCheckHandler(BudgetRepository budgetRepo, BudgetCacheService cacheService) {
        this.budgetRepo   = budgetRepo;
        this.cacheService = cacheService;
    }

    public void handle(RoutingContext ctx) {
        String userId   = ctx.get("userId");
        String category = ctx.pathParam("category");
        String amountStr = ctx.request().getParam("amount");

        if (amountStr == null) {
            ctx.response().setStatusCode(400).end("{\"error\":\"amount query param is required\"}");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("{\"error\":\"amount must be a number\"}");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        int year  = now.getYear();

        // Cache-first: try Redis, fall back to Postgres on miss
        cacheService.get(userId, category, month, year)
            .compose(cached -> {
                if (cached != null) return io.vertx.core.Future.succeededFuture(cached);
                // Cache miss — fetch from DB then populate cache for next time
                return budgetRepo.getOneCategorySpend(userId, category, month, year)
                    .compose(data -> {
                        if (data == null) return io.vertx.core.Future.succeededFuture((JsonObject) null);
                        return cacheService.set(userId, category, month, year, data)
                            .map(v -> data);
                    });
            })
            .onSuccess(data -> {
                // No limit set for this category — payment is always allowed
                if (data == null) {
                    ctx.response().setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                            .put("canAfford",       true)
                            .put("remaining",       -1)
                            .put("wouldExceed",     false)
                            .put("percentageAfter", -1)
                            .put("hasLimit",        false)
                            .encode());
                    return;
                }

                double limit      = data.getDouble("monthlyLimit");
                double spent      = data.getDouble("amountSpent");
                double remaining  = Math.max(0, limit - spent);
                double afterSpend = spent + amount;
                boolean wouldExceed  = afterSpend > limit;
                boolean canAfford    = !wouldExceed;
                int percentageAfter  = limit > 0 ? (int) ((afterSpend / limit) * 100) : 0;

                ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("canAfford",       canAfford)
                        .put("remaining",       remaining)
                        .put("wouldExceed",     wouldExceed)
                        .put("percentageAfter", percentageAfter)
                        .put("hasLimit",        true)
                        .encode());
            })
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .end("{\"error\":\"Budget check failed\"}"));
    }
}
