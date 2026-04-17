package com.akiba.auth.handlers;

import com.akiba.auth.services.MailService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.security.SecureRandom;

public class ForgotPasswordHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final MailService mailService;

  private static final int OTP_TTL_SECONDS = 600; // 10 minutes

  public ForgotPasswordHandler(Pool pgPool, RedisAPI redis, MailService mailService) {
    this.pgPool      = pgPool;
    this.redis       = redis;
    this.mailService = mailService;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String email = body.getString("email");
    if (email == null || email.isBlank()) {
      rejectWith(ctx, 400, "email is required");
      return;
    }

    lookupUser(email)
      .compose(user -> {
        if (user == null) {
          // Don't reveal whether the email exists — always respond the same way
          return Future.succeededFuture(null);
        }
        String otp    = generateOtp();
        String userId = user.getString("id");
        String name   = user.getString("full_name");

        return storeOtpInRedis(userId, otp)
          .compose(v -> mailService.sendPasswordResetOtp(email, name, otp))
          .map(v -> (JsonObject) null); // keep chain type consistent
      })
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "If that email is registered, a reset code has been sent.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[ForgotPasswordHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 500, "Something went wrong. Please try again.");
      });
  }

  // ─── Lookup user by email ─────────────────────────────────────────────────

  private Future<JsonObject> lookupUser(String email) {
    String sql = """
      SELECT id, full_name FROM auth.users
      WHERE email = $1 AND status = 'ACTIVE'
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(email))
      .map(rows -> {
        if (rows.rowCount() == 0) return null;
        var row = rows.iterator().next();
        return new JsonObject()
          .put("id",        row.getUUID("id").toString())
          .put("full_name", row.getString("full_name"));
      });
  }

  // ─── Store OTP in Redis with TTL ──────────────────────────────────────────

  private Future<Void> storeOtpInRedis(String userId, String otp) {
    String key = "pwd_reset:" + userId;
    return redis.setex(key, String.valueOf(OTP_TTL_SECONDS), otp).mapEmpty();
  }

  // ─── Generate 6-digit OTP ─────────────────────────────────────────────────

  private String generateOtp() {
    return String.format("%06d", new SecureRandom().nextInt(999999));
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
