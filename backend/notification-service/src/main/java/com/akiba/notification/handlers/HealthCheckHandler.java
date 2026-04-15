package com.akiba.notification.handlers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class HealthCheckHandler {

  private static final String VERSION          = "1.0.0";
  private static final long   DB_TIMEOUT_MS    = 2000;
  private static final long   REDIS_TIMEOUT_MS = 1000;

  private final Vertx          vertx;
  private final Pool           pgPool;
  private final RabbitMQClient rabbit;
  private final RedisAPI       redis;
  private final String         serviceName;
  private final long           startedAt;

  public HealthCheckHandler(
    Vertx vertx, Pool pgPool, RabbitMQClient rabbit, RedisAPI redis, String serviceName
  ) {
    this.vertx       = vertx;
    this.pgPool      = pgPool;
    this.rabbit      = rabbit;
    this.redis       = redis;
    this.serviceName = serviceName;
    this.startedAt   = System.currentTimeMillis();
  }

  public void handle(RoutingContext ctx) {
    long uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000;

    Future.all(checkDatabase(), checkRabbitMQ(), checkRedis())
      .onComplete(result -> {
        String dbStatus     = result.result().resultAt(0);
        String rabbitStatus = result.result().resultAt(1);
        String redisStatus  = result.result().resultAt(2);

        boolean allUp = "UP".equals(dbStatus)
          && "UP".equals(rabbitStatus)
          && "UP".equals(redisStatus);

        ctx.response()
          .setStatusCode(allUp ? 200 : 503)
          .putHeader("Content-Type", "application/json")
          .end(buildResponse(uptimeSeconds, dbStatus, rabbitStatus, redisStatus).encode());
      });
  }

  private Future<String> checkDatabase() {
    Future<String> dbCheck = pgPool.query("SELECT 1").execute()
      .map(rows -> "UP")
      .recover(err -> {
        System.err.println("[Health:" + serviceName + "] DB check failed: " + err.getMessage());
        return Future.succeededFuture("DOWN");
      });
    return raceWithTimeout(dbCheck, DB_TIMEOUT_MS);
  }

  private Future<String> checkRabbitMQ() {
    try {
      return Future.succeededFuture(rabbit.isConnected() ? "UP" : "DOWN");
    } catch (Exception e) {
      System.err.println("[Health:" + serviceName + "] RabbitMQ check threw: " + e.getMessage());
      return Future.succeededFuture("DOWN");
    }
  }

  private Future<String> checkRedis() {
    Future<String> pingCheck = redis.ping(java.util.List.of())
      .map(response -> "PONG".equalsIgnoreCase(response.toString()) ? "UP" : "DOWN")
      .recover(err -> {
        System.err.println("[Health:" + serviceName + "] Redis check failed: " + err.getMessage());
        return Future.succeededFuture("DOWN");
      });
    return raceWithTimeout(pingCheck, REDIS_TIMEOUT_MS);
  }

  private Future<String> raceWithTimeout(Future<String> check, long timeoutMs) {
    Promise<String> promise = Promise.promise();
    long timerId = vertx.setTimer(timeoutMs, id -> promise.tryComplete("DOWN"));
    check.onComplete(result -> {
      vertx.cancelTimer(timerId);
      promise.tryComplete(result.failed() ? "DOWN" : result.result());
    });
    return promise.future();
  }

  private JsonObject buildResponse(
    long uptimeSeconds, String dbStatus, String rabbitStatus, String redisStatus
  ) {
    boolean allUp = "UP".equals(dbStatus) && "UP".equals(rabbitStatus) && "UP".equals(redisStatus);
    return new JsonObject()
      .put("status",    allUp ? "UP" : "DOWN")
      .put("service",   serviceName)
      .put("version",   VERSION)
      .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
      .put("uptime",    uptimeSeconds)
      .put("checks", new JsonObject()
        .put("database", dbStatus)
        .put("rabbitmq", rabbitStatus)
        .put("redis",    redisStatus));
  }
}
