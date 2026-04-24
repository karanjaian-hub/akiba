package com.akiba.notification.verticles;

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
      .compose(v -> createTables())
      .onSuccess(v -> System.out.println("[NotificationService] Schema ready"))
      .onFailure(err -> System.err.println("[NotificationService] Schema failed: " + err.getMessage()));
  }

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS notifications")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createTables() {
    return Future.all(createAlertsTable(), createPreferencesTable()).mapEmpty();
  }

  private Future<Void> createAlertsTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS notifications.alerts (
                id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id    UUID         NOT NULL,
                type       VARCHAR(30)  NOT NULL,
                title      VARCHAR(100) NOT NULL,
                body       TEXT         NOT NULL,
                is_read    BOOLEAN      DEFAULT false,
                created_at TIMESTAMP    DEFAULT NOW()
            )
        """;
    return pgPool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createPreferencesTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS notifications.preferences (
                user_id         UUID PRIMARY KEY,
                push_token      TEXT,
                payment_enabled BOOLEAN DEFAULT true,
                budget_enabled  BOOLEAN DEFAULT true,
                savings_enabled BOOLEAN DEFAULT true,
                reports_enabled BOOLEAN DEFAULT true,
                system_enabled  BOOLEAN DEFAULT true,
                updated_at      TIMESTAMP DEFAULT NOW()
            )
        """;
    return pgPool.query(sql).execute().mapEmpty();
  }
}
