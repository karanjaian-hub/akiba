package com.akiba.transaction.verticles;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.sqlclient.Pool;

public class SchemaVerticle extends VerticleBase {

  private final Pool pgPool;

  public SchemaVerticle(Pool pgPool) {
    this.pgPool = pgPool;
  }

  @Override
  public Future<?> start() {
    return createSchema()
      .compose(v -> Future.all(createRecordsTable(), createImportsTable()))
      .onSuccess(v -> System.out.println("[TransactionService] Schema ready."))
      .onFailure(e -> System.err.println("[TransactionService] Schema failed: " + e.getMessage()));
  }

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS transactions")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createRecordsTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS transactions.records (
                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id     UUID NOT NULL,
                date        TIMESTAMP NOT NULL,
                amount      DECIMAL(15,2) NOT NULL,
                type        VARCHAR(10) NOT NULL,
                merchant    TEXT,
                category    VARCHAR(50),
                source      VARCHAR(20),
                reference   VARCHAR,
                raw_text    TEXT,
                anomalous   BOOLEAN DEFAULT false,
                created_at  TIMESTAMP DEFAULT NOW()
            )
            """;
    return pgPool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] records table failed: " + e.getMessage()))
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
    return pgPool.query(sql).execute()
      .onFailure(e -> System.err.println("[SchemaVerticle] imports table failed: " + e.getMessage()))
      .mapEmpty();
  }
}
