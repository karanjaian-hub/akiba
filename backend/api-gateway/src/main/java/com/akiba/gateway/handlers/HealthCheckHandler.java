//package com.akiba.gateway.handlers;
//
//import io.vertx.core.Future;
//import io.vertx.core.Promise;
//import io.vertx.core.Vertx;
//import io.vertx.core.json.JsonObject;
//import io.vertx.ext.web.RoutingContext;
//import io.vertx.sqlclient.Pool;
//import io.vertx.rabbitmq.RabbitMQClient;
//import io.vertx.redis.client.RedisAPI;
//
//import java.time.Instant;
//import java.time.format.DateTimeFormatter;
//
//public class HealthCheckHandler {
//
//    private static final String VERSION   = "1.0.0";
//    private static final long   DB_TIMEOUT_MS    = 2000;
//    private static final long   REDIS_TIMEOUT_MS = 1000;
//
//    private final Vertx vertx;
//    private final Pool pgPool;
//    private final RabbitMQClient rabbit;
//    private final RedisAPI redis;
//    private final String serviceName;
//    private final long startedAt; // epoch millis — set once when the handler is created
//
//    public HealthCheckHandler(
//        Vertx vertx,
//        Pool pgPool,
//        RabbitMQClient rabbit,
//        RedisAPI redis,
//        String serviceName
//    ) {
//        this.vertx       = vertx;
//        this.pgPool      = pgPool;
//        this.rabbit      = rabbit;
//        this.redis       = redis;
//        this.serviceName = serviceName;
//        this.startedAt   = System.currentTimeMillis();
//    }
//
//    /** Vert.x router handler — call this from your HttpVerticle's router. */
//    public void handle(RoutingContext ctx) {
//        long uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000;
//
//        // Run all 3 checks in parallel — none depends on another
//        Future.all(
//            checkDatabase(),
//            checkRabbitMQ(),
//            checkRedis()
//        )
//        .onComplete(result -> {
//            // result is always a success here — we handle failures inside each check
//            // so Future.all never sees a failed future; it always gets "UP" or "DOWN"
//            String dbStatus     = result.result().resultAt(0);
//            String rabbitStatus = result.result().resultAt(1);
//            String redisStatus  = result.result().resultAt(2);
//
//            boolean allUp = "UP".equals(dbStatus)
//                         && "UP".equals(rabbitStatus)
//                         && "UP".equals(redisStatus);
//
//            JsonObject body = buildResponse(
//                uptimeSeconds, dbStatus, rabbitStatus, redisStatus
//            );
//
//            ctx.response()
//               .setStatusCode(allUp ? 200 : 503)
//               .putHeader("Content-Type", "application/json")
//               .end(body.encode());
//        });
//    }
//
//    // ─── Individual checks ────────────────────────────────────────────────────
//
//    /**
//     * Races a SELECT 1 against a 2-second timeout.
//     * Always resolves to "UP" or "DOWN" — never fails the outer Future.all.
//     */
//    private Future<String> checkDatabase() {
//        Future<String> dbCheck = pgPool
//            .query("SELECT 1")
//            .execute()
//            .map(rows -> "UP")
//            .recover(err -> {
//                System.err.println("[Health:" + serviceName + "] DB check failed: " + err.getMessage());
//                return Future.succeededFuture("DOWN");
//            });
//
//        return raceWithTimeout(dbCheck, DB_TIMEOUT_MS, "DB");
//    }
//
//    /**
//     * RabbitMQ doesn't need a network round-trip — we check its in-process
//     * connection state. isConnected() returns false if the channel was closed
//     * or the broker disconnected.
//     */
//    private Future<String> checkRabbitMQ() {
//        try {
//            String status = rabbit.isConnected() ? "UP" : "DOWN";
//            return Future.succeededFuture(status);
//        } catch (Exception e) {
//            System.err.println("[Health:" + serviceName + "] RabbitMQ check threw: " + e.getMessage());
//            return Future.succeededFuture("DOWN");
//        }
//    }
//
//    /**
//     * Sends a PING command and expects PONG back.
//     * Races against a 1-second timeout (Redis should always be sub-10ms locally).
//     */
//    private Future<String> checkRedis() {
//        Future<String> pingCheck = redis
//            .ping(java.util.List.of())
//            .map(response -> "PONG".equalsIgnoreCase(response.toString()) ? "UP" : "DOWN")
//            .recover(err -> {
//                System.err.println("[Health:" + serviceName + "] Redis check failed: " + err.getMessage());
//                return Future.succeededFuture("DOWN");
//            });
//
//        return raceWithTimeout(pingCheck, REDIS_TIMEOUT_MS, "Redis");
//    }
//
//    // ─── Timeout helper ───────────────────────────────────────────────────────
//
//    /**
//     * Races a check future against a timer. Whichever completes first wins.
//     *
//     * Why Future.any()? We want the FIRST result — either the real answer
//     * or "DOWN" from the timeout — not both. Future.all() would wait for both.
//     *
//     * The timer future always resolves (never fails), so Future.any() will
//     * always find at least one successful future to return.
//     */
//    private Future<String> raceWithTimeout(Future<String> check, long timeoutMs, String label) {
//        Promise<String> timeoutPromise = Promise.promise();
//
//        long timerId = vertx.setTimer(timeoutMs, id ->
//            timeoutPromise.tryComplete("DOWN") // tryComplete — no-op if check already won
//        );
//
//        // When the real check finishes first, cancel the timer to avoid a
//        // spurious log message and resolve the timeout promise (no-op if late)
//        check.onComplete(result -> {
//            vertx.cancelTimer(timerId);
//            if (result.failed()) {
//                timeoutPromise.tryComplete("DOWN");
//            } else {
//                timeoutPromise.tryComplete(result.result());
//            }
//        });
//
//        return timeoutPromise.future();
//    }
//
//    // ─── Response builder ─────────────────────────────────────────────────────
//
//    private JsonObject buildResponse(
//        long uptimeSeconds,
//        String dbStatus,
//        String rabbitStatus,
//        String redisStatus
//    ) {
//        boolean allUp = "UP".equals(dbStatus) && "UP".equals(rabbitStatus) && "UP".equals(redisStatus);
//
//        return new JsonObject()
//            .put("status",    allUp ? "UP" : "DOWN")
//            .put("service",   serviceName)
//            .put("version",   VERSION)
//            .put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
//            .put("uptime",    uptimeSeconds)
//            .put("checks", new JsonObject()
//                .put("database", dbStatus)
//                .put("rabbitmq", rabbitStatus)
//                .put("redis",    redisStatus)
//            );
//    }
//}
