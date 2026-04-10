package com.akiba.budget.handlers;

import com.akiba.budget.repositories.BudgetRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class UpsertBudgetHandler {

    private final BudgetRepository budgetRepo;

    public UpsertBudgetHandler(BudgetRepository budgetRepo) {
        this.budgetRepo = budgetRepo;
    }

    public void handle(RoutingContext ctx) {
        String userId  = ctx.get("userId");
        JsonObject body = ctx.body().asJsonObject();

        if (body == null || !body.containsKey("category") || !body.containsKey("monthlyLimit")) {
            ctx.response().setStatusCode(400)
                .end("{\"error\":\"category and monthlyLimit are required\"}");
            return;
        }

        String category    = body.getString("category");
        double monthlyLimit = body.getDouble("monthlyLimit");

        if (monthlyLimit <= 0) {
            ctx.response().setStatusCode(400)
                .end("{\"error\":\"monthlyLimit must be greater than 0\"}");
            return;
        }

        budgetRepo.upsertLimit(userId, category, monthlyLimit)
            .onSuccess(v -> ctx.response()
                .setStatusCode(200)
                .end("{\"message\":\"Budget limit saved\"}"))
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .end("{\"error\":\"Failed to save budget limit\"}"));
    }
}
