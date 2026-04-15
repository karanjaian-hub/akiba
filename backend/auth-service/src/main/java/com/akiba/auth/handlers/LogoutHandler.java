package com.akiba.auth.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

public class LogoutHandler {

  private final Pool pgPool;
  private final RedisAPI redis;

  public LogoutHandler(Pool pgPool, RedisAPI redis) {
    this.pgPool = pgPool;
    this.redis  = redis;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String refreshToken  = body.getString("refreshToken");
    String jti           = ctx.get("jti");
    Integer remainingTtl = ctx.get("remainingTtl");

    if (refreshToken == null || jti == null) {
      rejectWith(ctx, 400, "refreshToken is required and request must be authenticated");
      return;
    }

    revokeRefreshToken(refreshToken)
      .compose(v -> blacklistJti(jti, remainingTtl != null ? remainingTtl : 900))
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Logged out successfully.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[LogoutHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 500, "Logout failed. Please try again.");
      });
  }

  private Future<Void> revokeRefreshToken(String refreshToken) {
    return pgPool.preparedQuery(
        "UPDATE auth.sessions SET revoked = true WHERE refresh_token = $1")
      .execute(Tuple.of(refreshToken))
      .mapEmpty();
  }

  private Future<Void> blacklistJti(String jti, int ttlSeconds) {
    if (ttlSeconds <= 0) return Future.succeededFuture(); // token already expired, no need to blacklist
    String key = "blacklist:" + jti;
    return redis.setex(key, String.valueOf(ttlSeconds), "revoked").mapEmpty();
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
