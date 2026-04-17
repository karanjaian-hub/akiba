package com.akiba.auth.handlers;

import at.favre.lib.crypto.bcrypt.BCrypt;
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

public class LoginHandler {

  private final Pool pgPool;
  private final RedisAPI redis;
  private final JWTAuth jwtAuth;

  private static final int ACCESS_TOKEN_MINUTES  = 15;
  private static final int REFRESH_TOKEN_DAYS    = 7;
  private static final int SESSION_TTL_SECONDS   = 900;

  public LoginHandler(Pool pgPool, RedisAPI redis, JWTAuth jwtAuth) {
    this.pgPool   = pgPool;
    this.redis    = redis;
    this.jwtAuth  = jwtAuth;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null) {
      rejectWith(ctx, 400, "Request body is required");
      return;
    }

    String email    = body.getString("email");
    String password = body.getString("password");

    if (email == null || password == null) {
      rejectWith(ctx, 400, "email and password are required");
      return;
    }

    loadUserByEmail(email)
      .compose(user -> verifyPassword(password, user))
      .compose(user -> checkAccountStatus(user))
      .compose(user -> loadUserPermissions(user))
      .compose(this::issueTokens)
      .onSuccess(tokens -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(tokens.encode()))
      .onFailure(err -> {
        System.err.println("[LoginHandler]  " + err.getMessage());
        rejectWith(ctx, 401, err.getMessage());
      });
  }

  private Future<JsonObject> loadUserByEmail(String email) {
    String sql = """
      SELECT u.id, u.full_name, u.email, u.phone,
             u.password_hash, u.status, u.role_id, r.name AS role_name
      FROM auth.users u
      JOIN auth.roles r ON r.id = u.role_id
      WHERE u.email = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(email))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Invalid email or password.");
        }
        Row row = rows.iterator().next();
        return Future.succeededFuture(new JsonObject()
          .put("id",           row.getUUID("id").toString())
          .put("fullName",     row.getString("full_name"))
          .put("email",        row.getString("email"))
          .put("passwordHash", row.getString("password_hash"))
          .put("status",       row.getString("status"))
          .put("roleId",       row.getUUID("role_id").toString())
          .put("roleName",     row.getString("role_name")));
      });
  }

  private Future<JsonObject> verifyPassword(String rawPassword, JsonObject user) {
    boolean matches = BCrypt.verifyer()
      .verify(rawPassword.toCharArray(), user.getString("passwordHash"))
      .verified;

    if (!matches) {
      return Future.failedFuture("Invalid email or password.");
    }
    return Future.succeededFuture(user);
  }

  private Future<JsonObject> checkAccountStatus(JsonObject user) {
    String status = user.getString("status");
    return switch (status) {
      case "ACTIVE"               -> Future.succeededFuture(user);
      case "PENDING_VERIFICATION" -> Future.failedFuture("Please verify your phone and email before logging in.");
      case "PHONE_VERIFIED"       -> Future.failedFuture("Please verify your email to complete registration.");
      case "EMAIL_VERIFIED"       -> Future.failedFuture("Please verify your phone to complete registration.");
      case "SUSPENDED"            -> Future.failedFuture("Your account has been suspended. Please contact support.");
      default                     -> Future.failedFuture("Account is not active.");
    };
  }

  private Future<JsonObject> loadUserPermissions(JsonObject user) {
    String sql = """
      SELECT p.name
      FROM auth.permissions p
      JOIN auth.role_permissions rp ON rp.permission_id = p.id
      WHERE rp.role_id = $1
      """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(user.getString("roleId"))))
      .compose(rows -> {
        JsonArray permissions = new JsonArray();
        rows.forEach(row -> permissions.add(row.getString("name")));
        user.put("permissions", permissions);
        return Future.succeededFuture(user);
      });
  }

  private Future<JsonObject> issueTokens(JsonObject user) {
    String jti          = UUID.randomUUID().toString();
    String refreshToken = UUID.randomUUID().toString();
    String userId       = user.getString("id");

    JsonObject claims = new JsonObject()
      .put("sub",         userId)
      .put("role",        user.getString("roleName"))
      .put("permissions", user.getJsonArray("permissions"))
      .put("jti",         jti);

    String accessToken = jwtAuth.generateToken(claims,
      new JWTOptions().setExpiresInMinutes(ACCESS_TOKEN_MINUTES));

    return saveRefreshToken(userId, refreshToken)
      .compose(v -> cacheSessionInRedis(userId, accessToken))
      .map(v -> new JsonObject()
        .put("accessToken",  accessToken)
        .put("refreshToken", refreshToken)
        .put("expiresIn",    ACCESS_TOKEN_MINUTES * 60)
        .put("user", new JsonObject()
          .put("id",       userId)
          .put("fullName", user.getString("fullName"))
          .put("email",    user.getString("email"))
          .put("role",     user.getString("roleName"))));
  }

  private Future<Void> saveRefreshToken(String userId, String refreshToken) {
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
    String key = "session:" + userId;
    return redis.setex(key, String.valueOf(SESSION_TTL_SECONDS), accessToken).mapEmpty();
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
