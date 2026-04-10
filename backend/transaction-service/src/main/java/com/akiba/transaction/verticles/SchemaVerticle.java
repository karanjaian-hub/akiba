package com.akiba.transaction.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;

public class SchemaVerticle extends AbstractVerticle {

  private final Pool pool;

  public SchemaVerticle(Pool pool) {
    this.pool = pool;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    createSchema()
      .compose(v -> createTablesInParallel())
      .onSuccess(v -> {
        System.out.println("[TransactionService] Schema ready.");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[TransactionService] Schema setup failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  private Future<Void> createSchema() {
    return pool.query("CREATE SCHEMA IF NOT EXISTS transactions")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createTablesInParallel() {
    // Both tables have no dependencies on each other, so we create them in parallel.
    // Future.all() waits for BOTH to finish before the chain continues.
    return Future.all(createRecordsTable(), createImportsTable()).mapEmpty();
  }

  private Future<Void> createRecordsTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS transactions.records (
                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id      UUID NOT NULL,
                date         TIMESTAMP NOT NULL,
                amount       DECIMAL(15,2) NOT NULL,
                type         VARCHAR(10) NOT NULL,      -- DEBIT or CREDIT
                merchant     TEXT,
                category     VARCHAR(50),
                source       VARCHAR(20),               -- MPESA_SMS, BANK_PDF, MANUAL
                reference    VARCHAR,
                raw_text     TEXT,
                anomalous    BOOLEAN DEFAULT false,      -- flagged if amount > 3x avg
                created_at   TIMESTAMP DEFAULT NOW()
            )
        """;
    return pool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] Failed to create records table: " + e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createImportsTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS transactions.imports (
                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id      UUID NOT NULL,
                source       VARCHAR(20) NOT NULL,
                file_name    TEXT,
                status       VARCHAR(20) DEFAULT 'PENDING',
                record_count INTEGER DEFAULT 0,
                created_at   TIMESTAMP DEFAULT NOW()
            )
        """;
    return pool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] Failed to create imports table: " + e.getMessage()))
      .mapEmpty();
  }
}
