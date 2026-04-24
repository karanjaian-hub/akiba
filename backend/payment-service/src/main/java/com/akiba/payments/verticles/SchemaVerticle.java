package com.akiba.payments.verticles;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs once on startup to ensure our DB tables exist.
 * Separating schema setup from business logic keeps MainVerticle clean.
 */
public class SchemaVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(SchemaVerticle.class);

  // Vert.x 5: PgPool is removed — use Pool from vertx-sql-client
  private final Pool pool;

  public SchemaVerticle(Pool pool) {
    this.pool = pool;
  }

  @Override
  public Future<?> start() {
    return createSchema()
      .compose(v -> createPaymentsTable())
      .compose(v -> createRecipientsTable())
      .onSuccess(v -> log.info("Payment schema ready"))
      .onFailure(err -> log.error("Payment schema setup failed", err));
  }

  private Future<Void> createSchema() {
    return pool.query("CREATE SCHEMA IF NOT EXISTS payments")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createPaymentsTable() {
    String sql = """
      CREATE TABLE IF NOT EXISTS payments.records (
        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id         UUID        NOT NULL,
        phone           VARCHAR(20) NOT NULL,
        amount          NUMERIC(12,2) NOT NULL,
        recipient_type  VARCHAR(10) NOT NULL,
        recipient_id    VARCHAR(50) NOT NULL,
        category        VARCHAR(50) NOT NULL,
        description     TEXT,
        account_ref     VARCHAR(50),
        status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',
        checkout_id     VARCHAR(100),
        mpesa_receipt   VARCHAR(50),
        created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createRecipientsTable() {
    // (user_id, identifier) is unique so we can UPSERT without duplicates
    String sql = """
      CREATE TABLE IF NOT EXISTS payments.recipients (
        id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id           UUID        NOT NULL,
        identifier        VARCHAR(50) NOT NULL,
        recipient_type    VARCHAR(10) NOT NULL,
        nickname          VARCHAR(100),
        default_category  VARCHAR(50),
        use_count         INT NOT NULL DEFAULT 1,
        created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE (user_id, identifier)
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }
}
