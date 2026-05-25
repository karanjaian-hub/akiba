package com.akiba.ai.verticles;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.launcher.application.VertxApplication;
import io.vertx.core.VerticleBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SchemaVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(SchemaVerticle.class);

  // Pool built inside start() using the VerticleBase-provided vertx instance.
  private Pool pgPool;

  @Override
  public Future<?> start() {
    pgPool = buildPgPool();

    return createSchema()
      .compose(v -> createTables())
      .onSuccess(v -> log.info("AI Service schema ready."))
      .onFailure(err -> log.error("Schema setup failed: {}", err.getMessage()));
  }


  public Pool getPgPool() {
    return pgPool;
  }

  private Pool buildPgPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"))
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new io.vertx.core.net.ClientSSLOptions().setTrustAll(true));

    return PgBuilder.pool()
      .connectingTo(connectOptions)
      .with(new PoolOptions().setMaxSize(3))
      .using(vertx)
      .build();
  }

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS ai")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createTables() {
    // conversations has no FK deps — create first, messages depends on it.
    return createConversationsTable()
      .compose(v -> Future.all(
        createMessagesTable(),   // FK → conversations
        createReportsTable()     // no FK deps
      ).mapEmpty());
  }

  private Future<Void> createConversationsTable() {
    return pgPool.query("""
                CREATE TABLE IF NOT EXISTS ai.conversations (
                    id          UUID PRIMARY KEY,
                    user_id     UUID NOT NULL,
                    title       VARCHAR(255),
                    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """).execute()
      .onFailure(e -> log.error("Failed to create ai.conversations: {}", e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createMessagesTable() {
    return pgPool.query("""
                CREATE TABLE IF NOT EXISTS ai.messages (
                    id               UUID PRIMARY KEY,
                    conversation_id  UUID NOT NULL REFERENCES ai.conversations(id) ON DELETE CASCADE,
                    role             VARCHAR(10) NOT NULL,
                    content          TEXT NOT NULL,
                    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """).execute()
      .onFailure(e -> log.error("Failed to create ai.messages: {}", e.getMessage()))
      .mapEmpty();
  }

  private Future<Void> createReportsTable() {
    return pgPool.query("""
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
                """).execute()
      .onFailure(e -> log.error("Failed to create ai.reports: {}", e.getMessage()))
      .mapEmpty();
  }
}
