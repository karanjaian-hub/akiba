package com.akiba.ai.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SchemaVerticle runs ONCE on startup and ensures the ai.* tables exist.
 *
 * Why a separate verticle? Because we want the schema to be guaranteed
 * before the HTTP server opens. We deploy SchemaVerticle first and only
 * then deploy MainVerticle. If the schema fails, the service fails fast
 * with a clear error instead of crashing on first DB query.
 *
 * Every statement uses IF NOT EXISTS so restarting the service is always safe.
 *
 * NOTE (Vert.x 5): Replaced PgPool with io.vertx.sqlclient.Pool — the
 * unified interface that works with any Vert.x SQL driver. PgPool extends
 * Pool, so you can still pass a PgPool instance here at the call site.
 * The old import path io.vertx.pgclient.PgPool still compiles, but Pool
 * is the idiomatic Vert.x 5 type for injection.
 */
public class SchemaVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(SchemaVerticle.class);

  private final Pool pool;

  public SchemaVerticle(Pool pool) {
    this.pool = pool;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    createSchema()
      .compose(v -> createTables())
      .onSuccess(v -> {
        log.info("AI Service schema ready.");
        startPromise.complete();
      })
      .onFailure(err -> {
        log.error("Schema setup failed: {}", err.getMessage());
        startPromise.fail(err);
      });
  }

  private Future<Void> createSchema() {
    return pool.query("CREATE SCHEMA IF NOT EXISTS ai")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createTables() {
    // Run all three table creations in parallel — they don't depend on each other.
    return Future.all(
      createConversationsTable(),
      createMessagesTable(),
      createReportsTable()
    ).mapEmpty();
  }

  private Future<Void> createConversationsTable() {
    return pool.query("""
        CREATE TABLE IF NOT EXISTS ai.conversations (
            id           UUID PRIMARY KEY,
            user_id      UUID NOT NULL,
            title        VARCHAR(255),
            created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
            updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
        )
        """)
      .execute()
      .onFailure(e -> log.error("Failed to create ai.conversations: {}", e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createMessagesTable() {
    return pool.query("""
        CREATE TABLE IF NOT EXISTS ai.messages (
            id               UUID PRIMARY KEY,
            conversation_id  UUID NOT NULL REFERENCES ai.conversations(id) ON DELETE CASCADE,
            role             VARCHAR(10) NOT NULL,
            content          TEXT NOT NULL,
            created_at       TIMESTAMP NOT NULL DEFAULT NOW()
        )
        """)
      .execute()
      .onFailure(e -> log.error("Failed to create ai.messages: {}", e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createReportsTable() {
    return pool.query("""
        CREATE TABLE IF NOT EXISTS ai.reports (
            id          UUID PRIMARY KEY,
            user_id     UUID NOT NULL,
            month       SMALLINT NOT NULL,
            year        SMALLINT NOT NULL,
            content     TEXT,
            status      VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
            created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
            UNIQUE(user_id, month, year)
        )
        """)
      .execute()
      .onFailure(e -> log.error("Failed to create ai.reports: {}", e.getMessage()))
      .mapEmpty();
  }
}
