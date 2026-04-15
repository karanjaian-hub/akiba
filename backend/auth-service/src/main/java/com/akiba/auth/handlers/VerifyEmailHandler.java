package com.akiba.auth.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class VerifyEmailHandler {

  private final Pool pgPool;

  public VerifyEmailHandler(Pool pgPool) {
    this.pgPool = pgPool;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String userId = body.getString("userId");
    String token  = body.getString("token");

    if (userId == null || token == null) {
      rejectWith(ctx, 400, "userId and token are required");
      return;
    }

    validateEmailToken(userId, token)
      .compose(v -> markEmailVerified(userId))
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("message", "Email verified successfully.")
          .encode()))
      .onFailure(err -> {
        System.err.println("[VerifyEmailHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 400, err.getMessage());
      });
  }

  private Future<Void> validateEmailToken(String userId, String token) {
    String sql = """
      SELECT id FROM auth.otps
      WHERE user_id = $1
        AND otp_code = $2
        AND type = 'EMAIL'
        AND used = false
        AND expires_at > NOW()
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId), token))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Invalid or expired email verification link.");
        }
        return markTokenUsed(userId, token);
      });
  }

  private Future<Void> markTokenUsed(String userId, String token) {
    String sql = """
      UPDATE auth.otps SET used = true
      WHERE user_id = $1 AND otp_code = $2 AND type = 'EMAIL'
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId), token))
      .mapEmpty();
  }

  private Future<Void> markEmailVerified(String userId) {
    String sql = """
      UPDATE auth.users
      SET status = CASE
        WHEN status = 'PHONE_VERIFIED' THEN 'ACTIVE'
        ELSE 'EMAIL_VERIFIED'
      END,
      updated_at = NOW()
      WHERE id = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .mapEmpty();
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
