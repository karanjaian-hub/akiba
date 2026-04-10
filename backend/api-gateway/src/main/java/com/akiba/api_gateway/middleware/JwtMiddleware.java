package com.akiba.api_gateway.middleware;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;

/**
 * Verifies JWT on every protected route.
 * Checks the Redis blacklist for revoked tokens (logout).
 * Attaches userId, role, and jti to RoutingContext for downstream handlers.
 */
public class JwtMiddleware {

  private final JWTAuth jwtAuth;
  private final RedisAPI redis;

  public JwtMiddleware(JWTAuth jwtAuth, RedisAPI redis) {
    this.jwtAuth = jwtAuth;
    this.redis   = redis;
  }

  public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      rejectWith(ctx, 401, "Authorization header is required");
      return;
    }

    String token = authHeader.substring(7);

    jwtAuth.authenticate(new TokenCredentials(token))
      .compose(user -> {
        JsonObject principal = user.principal();
        String jti           = principal.getString("jti");

        // Check if this token was explicitly revoked via logout
        return redis.get("blacklist:" + jti)
          .compose(blacklisted -> {
            if (blacklisted != null) {
              return io.vertx.core.Future.failedFuture("Token has been revoked.");
            }

            // Attach claims to context so downstream handlers don't re-parse JWT
            ctx.put("userId",      principal.getString("sub"));
            ctx.put("role",        principal.getString("role"));
            ctx.put("permissions", principal.getJsonArray("permissions"));
            ctx.put("jti",         jti);

            // Calculate remaining TTL for blacklisting on logout
            long exp         = principal.getLong("exp", 0L);
            long now         = System.currentTimeMillis() / 1000;
            int remainingTtl = (int) Math.max(0, exp - now);
            ctx.put("remainingTtl", remainingTtl);

            return io.vertx.core.Future.succeededFuture();
          });
      })
      .onSuccess(v -> ctx.next())
      .onFailure(err -> {
        System.err.println("[JwtMiddleware] ❌ " + err.getMessage());
        rejectWith(ctx, 401, "Invalid or expired token.");
      });
  }

  private void rejectWith(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
