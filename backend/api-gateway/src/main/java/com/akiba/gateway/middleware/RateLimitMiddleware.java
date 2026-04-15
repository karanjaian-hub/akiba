package com.akiba.gateway.middleware;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;

import java.util.List;

// Redis-backed rate limiter for payment routes.
// Allows max 5 payment requests per user per minute.

public class RateLimitMiddleware {

  private final RedisAPI redis;

  private static final int MAX_REQUESTS_PER_MINUTE = 5;

  public RateLimitMiddleware(RedisAPI redis) {
    this.redis = redis;
  }

  public void handle(RoutingContext ctx) {
    String userId = ctx.get("userId");

    if (userId == null) {
      rejectWith(ctx, 401, "Unauthorized");
      return;
    }

    // Key pattern matches the guide spec: ratelimit:{userId}:payments
    String key = "ratelimit:" + userId + ":payments";

    redis.incr(key)
      .compose(count -> {
        long requestCount = count.toLong();

        if (requestCount == 1) {
          // First request in this window — set the 60s expiry
          // INCR alone doesn't expire, so we set TTL on first hit
          return redis.expire(List.of(key, "60"))
            .map(v -> requestCount);
        }
        return io.vertx.core.Future.succeededFuture(requestCount);
      })
      .onSuccess(count -> {
        if (count > MAX_REQUESTS_PER_MINUTE) {
          System.err.printf("[RateLimitMiddleware] ⚠️ userId=%s exceeded payment rate limit%n", userId);
          ctx.response()
            .setStatusCode(429)
            .putHeader("Content-Type", "application/json")
            .putHeader("Retry-After", "60")
            .end(new JsonObject()
              .put("error", "Too many payment requests. Please wait a minute before trying again.")
              .encode());
          return;
        }
        ctx.next();
      })
      .onFailure(err -> {
        // If Redis is down, fail open — don't block payments due to infra issue
        System.err.println("[RateLimitMiddleware] ❌ Redis error: " + err.getMessage());
        ctx.next();
      });
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
