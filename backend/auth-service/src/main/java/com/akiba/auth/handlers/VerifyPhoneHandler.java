package com.akiba.auth.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.UUID;

/**
 * Handles POST /auth/verify-phone
 */
public class VerifyPhoneHandler {

  private final Pool pgPool;
  private final RedisAPI redis;

  public VerifyPhoneHandler(Pool pgPool, RedisAPI redis) {
    this.pgPool = pgPool;
    this.redis = redis;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String userId = body.getString("userId");
    String otp    = body.getString("otp");

    if (userId == null || otp == null) {
      rejectWith(ctx, 400, "userId and otp are required");
      return;
    }

    String redisKey = "otp:" + userId + ":phone";

    redis.get(redisKey)
      .compose(storedOtp -> validateOtp(storedOtp, otp, userId))
      .compose(v -> markPhoneVerified(userId))
      .compose(v -> deleteOtpFromRedis(redisKey))
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Phone verified successfully.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[VerifyPhoneHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 400, err.getMessage());
      });
  }

  private Future<Void> validateOtp(io.vertx.redis.client.Response storedOtp,
                                   String providedOtp, String userId) {
    if (storedOtp == null) {
      return Future.failedFuture("OTP has expired or is invalid. Please request a new one.");
    }
    if (!storedOtp.toString().equals(providedOtp)) {
      return Future.failedFuture("Invalid OTP. Please check and try again.");
    }
    return Future.succeededFuture();
  }

  private Future<Void> markPhoneVerified(String userId) {
    String sql = """
      UPDATE auth.users
      SET status = CASE
        WHEN status = 'EMAIL_VERIFIED' THEN 'ACTIVE'
        ELSE 'PHONE_VERIFIED'
      END,
      updated_at = NOW()
      WHERE id = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .mapEmpty();
  }

  private Future<Void> deleteOtpFromRedis(String redisKey) {
    return redis.del(List.of(redisKey)).mapEmpty();
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
