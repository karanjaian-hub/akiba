package com.akiba.budget.handlers;

import com.akiba.budget.repositories.BudgetRepository;
import io.vertx.ext.web.RoutingContext;

import java.time.LocalDateTime;

public class GetBudgetsHandler {

    private final BudgetRepository budgetRepo;

    public GetBudgetsHandler(BudgetRepository budgetRepo) {
        this.budgetRepo = budgetRepo;
    }

    public void handle(RoutingContext ctx) {
        String userId = ctx.get("userId");
        LocalDateTime now = LocalDateTime.now();

        budgetRepo.getAllWithSpend(userId, now.getMonthValue(), now.getYear())
            .onSuccess(budgets -> ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(budgets.encode()))
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .end("{\"error\":\"Failed to fetch budgets\"}"));
    }
}
