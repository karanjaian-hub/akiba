package com.akiba.payments.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs once on startup to ensure our DB tables exist.
 * Separating schema setup from business logic keeps MainVerticle clean.
 */
public class SchemaVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(SchemaVerticle.class);

  private final PgPool pgPool;

  public SchemaVerticle(PgPool pgPool) {
    this.pgPool = pgPool;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    createSchema()
      .compose(v -> createPaymentsTable())
      .compose(v -> createRecipientsTable())
      .onSuccess(v -> {
        log.info("Payment schema ready");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS payments")
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
        recipient_type  VARCHAR(10) NOT NULL,       -- PHONE | TILL | PAYBILL
        recipient_id    VARCHAR(50) NOT NULL,
        category        VARCHAR(50) NOT NULL,
        description     TEXT,
        account_ref     VARCHAR(50),
        status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',  -- PENDING | COMPLETED | FAILED
        checkout_id     VARCHAR(100),               -- M-Pesa CheckoutRequestID
        mpesa_receipt   VARCHAR(50),                -- returned on COMPLETED
        created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
      """;
    return pgPool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createRecipientsTable() {
    // Recipients are auto-saved per user so they can quickly re-pay the same person.
    // (user_id, identifier) is unique so we can UPSERT without duplicates.
    String sql = """
      CREATE TABLE IF NOT EXISTS payments.recipients (
        id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id           UUID        NOT NULL,
        identifier        VARCHAR(50) NOT NULL,   -- phone number, till, or paybill
        recipient_type    VARCHAR(10) NOT NULL,
        nickname          VARCHAR(100),
        default_category  VARCHAR(50),
        use_count         INT NOT NULL DEFAULT 1,
        created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE (user_id, identifier)
      )
      """;
    return pgPool.query(sql).execute().mapEmpty();
  }
}
