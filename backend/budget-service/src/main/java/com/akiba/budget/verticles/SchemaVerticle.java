package com.akiba.budget.verticles;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.sqlclient.Pool;

public class SchemaVerticle extends VerticleBase {

  private final Pool pool;

  public SchemaVerticle(Pool pool) {
    this.pool = pool;
  }

  @Override
  public Future<?> start() {
    return createSchema()
      .compose(v -> Future.all(createLimitsTable(), createSpendTrackingTable()))
      .onSuccess(v -> System.out.println("[BudgetService] Schema ready."))
      .onFailure(e -> System.err.println("[BudgetService] Schema failed: " + e.getMessage()));
  }

  private Future<Void> createSchema() {
    return pool.query("CREATE SCHEMA IF NOT EXISTS budgets")
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
    return pool.query(sql).execute()
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
    return pool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] spend_tracking table failed: " + e.getMessage()))
      .mapEmpty();
  }
}
