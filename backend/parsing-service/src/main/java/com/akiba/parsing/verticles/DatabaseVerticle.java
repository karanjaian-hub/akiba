package com.akiba.parsing.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Runs at boot time only — creates the parsing schema and tables
 * if they don't exist yet, then completes.
 * Uses IF NOT EXISTS so it is safe to re-run on every restart (idempotent).
 */
public class DatabaseVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    Pool pool = createPool();

    createSchema(pool)
      .compose(v -> createJobsTable(pool))
      .compose(v -> createResultsTable(pool))
      .compose(v -> pool.close())          // close() returns Future<Void> in Vert.x 5
      .onSuccess(v -> {
        System.out.println("[DatabaseVerticle] Schema ready.");
        startPromise.complete();
      })
      .onFailure(err -> {
        pool.close();                      // best-effort close on failure path
        startPromise.fail(err);
      });
  }

  private Pool createPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "localhost"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(2))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  private Future<Void> createSchema(Pool pool) {
    return pool.query("CREATE SCHEMA IF NOT EXISTS parsing")
      .execute()
      .mapEmpty();
  }

  private Future<Void> createJobsTable(Pool pool) {
    String sql = """
      CREATE TABLE IF NOT EXISTS parsing.jobs (
          job_id       VARCHAR(64)  PRIMARY KEY,
          user_id      VARCHAR(64)  NOT NULL,
          type         VARCHAR(20)  NOT NULL,
          status       VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
          error_msg    TEXT,
          created_at   BIGINT       NOT NULL,
          updated_at   BIGINT       NOT NULL
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createResultsTable(Pool pool) {
    String sql = """
      CREATE TABLE IF NOT EXISTS parsing.results (
          id          BIGSERIAL     PRIMARY KEY,
          job_id      VARCHAR(64)   NOT NULL REFERENCES parsing.jobs(job_id),
          user_id     VARCHAR(64)   NOT NULL,
          date        VARCHAR(32),
          amount      NUMERIC(15,2),
          type        VARCHAR(10),
          merchant    TEXT,
          reference   VARCHAR(128),
          raw_text    TEXT,
          category    VARCHAR(64),
          balance     NUMERIC(15,2),
          created_at  BIGINT        NOT NULL
      )
      """;
    return pool.query(sql).execute().mapEmpty();
  }
}
