package com.akiba.savings.verticles;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(SchemaVerticle.class);

  private final Pool pool;

  public SchemaVerticle(Pool pool) {
    this.pool = pool;
  }

  @Override
  public Future<?> start() {
    return createSchema()
      .compose(v -> createGoalsTable())
      .compose(v -> createContributionsTable())
      .onSuccess(v -> log.info("Savings schema ready"))
      .onFailure(err -> log.error("Savings schema setup failed", err));
  }

  private Future<Void> createSchema() {
    return pool.query("CREATE SCHEMA IF NOT EXISTS savings").execute().mapEmpty();
  }

  private Future<Void> createGoalsTable() {
    String sql = """
      CREATE TABLE IF NOT EXISTS savings.goals (
        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id         UUID           NOT NULL,
        name            VARCHAR(100)   NOT NULL,
        target_amount   DECIMAL(15,2)  NOT NULL,
        current_amount  DECIMAL(15,2)  NOT NULL DEFAULT 0,
        deadline        DATE           NOT NULL,
        icon            VARCHAR(50),
        status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createContributionsTable() {
    String sql = """
      CREATE TABLE IF NOT EXISTS savings.contributions (
        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        goal_id         UUID           NOT NULL REFERENCES savings.goals(id),
        user_id         UUID           NOT NULL,
        amount          DECIMAL(15,2)  NOT NULL,
        transaction_id  VARCHAR(100),
        note            TEXT,
        created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }
}
