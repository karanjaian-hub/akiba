package com.akiba.auth.handlers;

import com.akiba.auth.services.MailService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.security.SecureRandom;

public class ResendVerificationHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final MailService mailService;

  private static final int OTP_TTL_SECONDS = 86400; // 24 hours

  public ResendVerificationHandler(Pool pgPool, RedisAPI redis, MailService mailService) {
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

    lookupUnverifiedUser(email)
      .compose(user -> {
        if (user == null) {
          // Don't reveal whether the email exists or is already verified
          return Future.succeededFuture();
        }
        String otp    = generateOtp();
        String userId = user.getString("id");
        String name   = user.getString("full_name");

        return storeOtpInRedis(userId, otp)
          .compose(v -> mailService.sendVerificationOtp(email, name, otp));
      })
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "If that email is pending verification, a new code has been sent.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[ResendVerificationHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 500, "Something went wrong. Please try again.");
      });
  }

  private Future<JsonObject> lookupUnverifiedUser(String email) {
    String sql = """
      SELECT id, full_name FROM auth.users
      WHERE email = $1 AND status = 'PENDING_VERIFICATION'
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

  private Future<Void> storeOtpInRedis(String userId, String otp) {
    String key = "email_verify:" + userId;
    // SETEX overwrites any existing OTP, resetting the TTL
    return redis.setex(key, String.valueOf(OTP_TTL_SECONDS), otp).mapEmpty();
  }

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
