package com.akiba.auth.handlers;
import at.favre.lib.crypto.bcrypt.BCrypt;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.UUID;

public class ResetpasswordHandler {
  private final Pool pgPool;
  private final RedisAPI redis;

  public ResetpasswordHandler(Pool pgPool, RedisAPI redis) {
    this.pgPool = pgPool;
    this.redis  = redis;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String userId      = body.getString("userId");
    String otp         = body.getString("otp");
    String newPassword = body.getString("newPassword");

    if (userId == null || otp == null || newPassword == null ||
      userId.isBlank() || otp.isBlank() || newPassword.isBlank()) {
      rejectWith(ctx, 400, "userId, otp and newPassword are required");
      return;
    }

    if (newPassword.length() < 8) {
      rejectWith(ctx, 400, "Password must be at least 8 characters");
      return;
    }

    String redisKey = "pwd_reset:" + userId;

    validateOtp(redisKey, otp)
      .compose(v -> updatePassword(userId, newPassword))
      .compose(v -> deleteOtpFromRedis(redisKey))
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Password reset successfully. You can now log in.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[ResetPasswordHandler] ❌ " + err.getMessage());
        // Keep error messages user-friendly but not revealing
        String userMessage = err.getMessage().startsWith("Invalid") || err.getMessage().startsWith("Expired")
          ? err.getMessage()
          : "Password reset failed. Please try again.";
        rejectWith(ctx, 400, userMessage);
      });
  }

  // ─── Validate OTP from Redis ──────────────────────────────────────────────

  private Future<Void> validateOtp(String redisKey, String providedOtp) {
    return redis.get(redisKey)
      .compose(storedOtp -> {
        if (storedOtp == null) {
          return Future.failedFuture("Expired or invalid reset code. Please request a new one.");
        }
        if (!storedOtp.toString().equals(providedOtp)) {
          return Future.failedFuture("Invalid reset code. Please check and try again.");
        }
        return Future.<Void>succeededFuture();
      });
  }

  // ─── Update password hash in DB ───────────────────────────────────────────

  private Future<Void> updatePassword(String userId, String newPassword) {
    String passwordHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
    String sql = """
      UPDATE auth.users
      SET password_hash = $1, updated_at = NOW()
      WHERE id = $2 AND status = 'ACTIVE'
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(passwordHash, UUID.fromString(userId)))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Invalid or inactive account.");
        }
        return Future.<Void>succeededFuture();
      });
  }

  // ─── Delete OTP from Redis ────────────────────────────────────────────────

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
