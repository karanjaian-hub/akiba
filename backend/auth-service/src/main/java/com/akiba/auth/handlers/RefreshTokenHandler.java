package com.akiba.auth.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Handles POST /auth/refresh
 */
public class RefreshTokenHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final JWTAuth jwtAuth;

  private static final int ACCESS_TOKEN_MINUTES = 15;
  private static final int REFRESH_TOKEN_DAYS   = 7;
  private static final int SESSION_TTL_SECONDS  = 900;

  public RefreshTokenHandler(Pool pgPool, RedisAPI redis, JWTAuth jwtAuth) {
    this.pgPool  = pgPool;
    this.redis   = redis;
    this.jwtAuth = jwtAuth;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String refreshToken = body.getString("refreshToken");

    if (refreshToken == null || refreshToken.isBlank()) {
      rejectWith(ctx, 400, "refreshToken is required");
      return;
    }

    loadSession(refreshToken)
      .compose(session -> loadUserWithPermissions(session))
      .compose(data -> rotateTokens(data))
      .onSuccess(tokens -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(tokens.encode()))
      .onFailure(err -> {
        System.err.println("[RefreshTokenHandler] ❌ " + err.getMessage());
        rejectWith(ctx, 401, err.getMessage());
      });
  }

  private Future<JsonObject> loadSession(String refreshToken) {
    String sql = """
      SELECT s.id, s.user_id, s.expires_at, s.revoked
      FROM auth.sessions s
      WHERE s.refresh_token = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(refreshToken))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Invalid refresh token.");
        }
        Row row = rows.iterator().next();

        if (row.getBoolean("revoked")) {
          return Future.failedFuture("Refresh token has been revoked.");
        }

        if (row.getLocalDateTime("expires_at").toInstant(java.time.ZoneOffset.UTC)
          .isBefore(Instant.now())) {
          return Future.failedFuture("Refresh token has expired. Please log in again.");
        }

        return Future.succeededFuture(new JsonObject()
          .put("sessionId",    row.getUUID("id").toString())
          .put("userId",       row.getUUID("user_id").toString())
          .put("refreshToken", refreshToken));
      });
  }

  private Future<JsonObject> loadUserWithPermissions(JsonObject session) {
    String userId = session.getString("userId");
    String sql = """
      SELECT u.id, u.full_name, u.email, u.role_id, r.name AS role_name
      FROM auth.users u
      JOIN auth.roles r ON r.id = u.role_id
      WHERE u.id = $1 AND u.status = 'ACTIVE'
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("User not found or account is not active.");
        }
        Row row = rows.iterator().next();
        JsonObject user = new JsonObject()
          .put("id",       row.getUUID("id").toString())
          .put("fullName", row.getString("full_name"))
          .put("email",    row.getString("email"))
          .put("roleId",   row.getUUID("role_id").toString())
          .put("roleName", row.getString("role_name"));

        return loadPermissions(user, session);
      });
  }

  private Future<JsonObject> loadPermissions(JsonObject user, JsonObject session) {
    String sql = """
      SELECT p.name FROM auth.permissions p
      JOIN auth.role_permissions rp ON rp.permission_id = p.id
      WHERE rp.role_id = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(user.getString("roleId"))))
      .compose(rows -> {
        JsonArray permissions = new JsonArray();
        rows.forEach(row -> permissions.add(row.getString("name")));
        user.put("permissions", permissions);
        user.put("sessionId",    session.getString("sessionId"));
        user.put("oldRefreshToken", session.getString("refreshToken"));
        return Future.succeededFuture(user);
      });
  }

  private Future<JsonObject> rotateTokens(JsonObject user) {
    String jti             = UUID.randomUUID().toString();
    String newRefreshToken = UUID.randomUUID().toString();
    String userId          = user.getString("id");

    JsonObject claims = new JsonObject()
      .put("sub",         userId)
      .put("role",        user.getString("roleName"))
      .put("permissions", user.getJsonArray("permissions"))
      .put("jti",         jti);

    String newAccessToken = jwtAuth.generateToken(claims,
      new JWTOptions().setExpiresInMinutes(ACCESS_TOKEN_MINUTES));

    return revokeOldRefreshToken(user.getString("oldRefreshToken"))
      .compose(v -> saveNewRefreshToken(userId, newRefreshToken))
      .compose(v -> cacheSessionInRedis(userId, newAccessToken))
      .map(v -> new JsonObject()
        .put("accessToken",  newAccessToken)
        .put("refreshToken", newRefreshToken)
        .put("expiresIn",    ACCESS_TOKEN_MINUTES * 60));
  }

  private Future<Void> revokeOldRefreshToken(String refreshToken) {
    return pgPool.preparedQuery(
        "UPDATE auth.sessions SET revoked = true WHERE refresh_token = $1")
      .execute(Tuple.of(refreshToken))
      .mapEmpty();
  }

  private Future<Void> saveNewRefreshToken(String userId, String refreshToken) {
    String sql = """
      INSERT INTO auth.sessions (user_id, refresh_token, expires_at)
      VALUES ($1, $2, $3)
      """;
    Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(
        UUID.fromString(userId),
        refreshToken,
        java.time.LocalDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC)))
      .mapEmpty();
  }

  private Future<Void> cacheSessionInRedis(String userId, String accessToken) {
    return redis.setex("session:" + userId,
      String.valueOf(SESSION_TTL_SECONDS), accessToken).mapEmpty();
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
