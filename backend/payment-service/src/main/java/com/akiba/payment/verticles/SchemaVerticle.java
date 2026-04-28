package com.akiba.payment.verticles;

import com.akiba.payment.config.PaymentConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlClient;

/**
 * SchemaVerticle runs ONCE at startup to make sure the DB tables exist.
 *
 * All statements use IF NOT EXISTS — so restarting the service is always safe.
 * We deploy this first and only start the HTTP server after it completes.
 */
public class SchemaVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    Pool db = PaymentConfig.createPgPool(vertx);

    createSchema(db)
      .compose(v -> createPaymentsTable(db))
      .compose(v -> createRecipientsTable(db))
      .onSuccess(v -> {
        System.out.println("[payment-service] Schema ready ✓");
        db.close();
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[payment-service] Schema creation failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  private Future<Void> createSchema(SqlClient db) {
    return db.query("CREATE SCHEMA IF NOT EXISTS payments")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createPaymentsTable(SqlClient db) {
    String sql = """
            CREATE TABLE IF NOT EXISTS payments.records (
                id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id             UUID        NOT NULL,
                phone               VARCHAR(20) NOT NULL,
                account_ref         VARCHAR(100),
                amount              DECIMAL(15,2) NOT NULL,
                category            VARCHAR(50),
                type                VARCHAR(10) NOT NULL,
                status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                checkout_request_id VARCHAR(100),
                merchant_request_id VARCHAR(100),
                result_code         VARCHAR(10),
                result_desc         TEXT,
                created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
                updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
            )
            """;
    return db.query(sql).execute().mapEmpty();
  }

  private Future<Void> createRecipientsTable(SqlClient db) {
    String sql = """
            CREATE TABLE IF NOT EXISTS payments.recipients (
                id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id          UUID         NOT NULL,
                identifier       VARCHAR(30)  NOT NULL,
                nickname         VARCHAR(100),
                default_category VARCHAR(50),
                type             VARCHAR(10)  NOT NULL,
                use_count        INT          NOT NULL DEFAULT 1,
                last_used_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
                created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
                UNIQUE (user_id, identifier)
            )
            """;
    return db.query(sql).execute().mapEmpty();
  }
}
