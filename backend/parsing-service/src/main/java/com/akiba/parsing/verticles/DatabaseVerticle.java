package com.akiba.parsing.verticles;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    Pool pool = createPool();
    return createSchema(pool)
      .compose(v -> createJobsTable(pool))
      .compose(v -> createResultsTable(pool))
      .compose(v -> pool.close())
      .onSuccess(v -> System.out.println("[DatabaseVerticle] Schema ready."))
      .onFailure(err -> pool.close());
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
    return pool.query("CREATE SCHEMA IF NOT EXISTS parsing").execute().mapEmpty();
  }

  private Future<Void> createJobsTable(Pool pool) {
    String sql = """
            CREATE TABLE IF NOT EXISTS parsing.jobs (
                job_id      VARCHAR(64)  PRIMARY KEY,
                user_id     VARCHAR(64)  NOT NULL,
                type        VARCHAR(20)  NOT NULL,
                status      VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
                error_msg   TEXT,
                created_at  BIGINT       NOT NULL,
                updated_at  BIGINT       NOT NULL
            )
            """;
    return pool.query(sql).execute().mapEmpty();
  }

  private Future<Void> createResultsTable(Pool pool) {
    String sql = """
            CREATE TABLE IF NOT EXISTS parsing.results (
                id          BIGSERIAL    PRIMARY KEY,
                job_id      VARCHAR(64)  NOT NULL REFERENCES parsing.jobs(job_id),
                user_id     VARCHAR(64)  NOT NULL,
                date        VARCHAR(32),
                amount      NUMERIC(15,2),
                type        VARCHAR(10),
                merchant    TEXT,
                reference   VARCHAR(128),
                raw_text    TEXT,
                category    VARCHAR(64),
                balance     NUMERIC(15,2),
                created_at  BIGINT       NOT NULL
            )
            """;
    return pool.query(sql).execute().mapEmpty();
  }
}
