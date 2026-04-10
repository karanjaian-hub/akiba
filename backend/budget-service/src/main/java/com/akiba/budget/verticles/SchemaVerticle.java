package com.akiba.budget.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;  // ← changed

public class SchemaVerticle extends AbstractVerticle {

  private final Pool pgPool;  // ← changed

  public SchemaVerticle(Pool pgPool) {  // ← changed
    this.pgPool = pgPool;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    createSchema()
      .compose(v -> Future.all(createLimitsTable(), createSpendTrackingTable()))
      .onSuccess(v -> {
        System.out.println("[BudgetService] Schema ready.");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[BudgetService] Schema setup failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS budgets")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createLimitsTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS budgets.limits (
                id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id       UUID NOT NULL,
                category      VARCHAR(50) NOT NULL,
                monthly_limit DECIMAL(15,2) NOT NULL,
                created_at    TIMESTAMP DEFAULT NOW(),
                updated_at    TIMESTAMP DEFAULT NOW(),
                UNIQUE(user_id, category)
            )
            """;
    return pgPool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] limits table failed: " + e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createSpendTrackingTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS budgets.spend_tracking (
                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id      UUID NOT NULL,
                category     VARCHAR(50) NOT NULL,
                month        INTEGER NOT NULL,
                year         INTEGER NOT NULL,
                amount_spent DECIMAL(15,2) DEFAULT 0,
                updated_at   TIMESTAMP DEFAULT NOW(),
                UNIQUE(user_id, category, month, year)
            )
            """;
    return pgPool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] spend_tracking table failed: " + e.getMessage()))
      .mapEmpty();
  }
}
