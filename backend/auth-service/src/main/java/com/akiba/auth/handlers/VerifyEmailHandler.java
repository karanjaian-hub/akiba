package com.akiba.auth.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.UUID;

public class VerifyEmailHandler {

  private final Pool pgPool;
  private final RedisAPI redis;

  public VerifyEmailHandler(Pool pgPool, RedisAPI redis) {
    this.pgPool = pgPool;
    this.redis  = redis;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String userId = body.getString("userId");
    String otp    = body.getString("otp");

    if (userId == null || otp == null || userId.isBlank() || otp.isBlank()) {
      rejectWith(ctx, 400, "userId and otp are required");
      return;
    }

    String redisKey = "email_verify:" + userId;

    validateOtp(redisKey, otp)
      .compose(v -> activateUser(userId))
      .compose(v -> deleteOtpFromRedis(redisKey))
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Email verified successfully. You can now log in.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[VerifyEmailHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 400, err.getMessage());
      });
  }

  private Future<Void> validateOtp(String redisKey, String providedOtp) {
    return redis.get(redisKey)
      .compose(storedOtp -> {
        if (storedOtp == null) {
          return Future.failedFuture("Verification code has expired. Please request a new one.");
        }
        if (!storedOtp.toString().equals(providedOtp)) {
          return Future.failedFuture("Invalid verification code. Please check and try again.");
        }
        return Future.<Void>succeededFuture();
      });
  }

  private Future<Void> activateUser(String userId) {
    String sql = """
      UPDATE auth.users
      SET status = 'ACTIVE', updated_at = NOW()
      WHERE id = $1 AND status = 'PENDING_VERIFICATION'
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Account not found or already verified.");
        }
        return Future.<Void>succeededFuture();
      });
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
