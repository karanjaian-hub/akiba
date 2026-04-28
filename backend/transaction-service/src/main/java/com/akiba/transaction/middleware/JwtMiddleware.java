package com.akiba.transaction.middleware;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;

public class JwtMiddleware implements Handler<RoutingContext> {

  private final JWTAuth jwtAuth;

  public JwtMiddleware(JWTAuth jwtAuth) {
    this.jwtAuth = jwtAuth;
  }

  @Override
  public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      unauthorized(ctx, "Missing or malformed Authorization header");
      return;
    }

    String token = authHeader.substring(7);

    // Vert.x 5: authenticate() requires TokenCredentials, not a raw JsonObject
    jwtAuth.authenticate(new TokenCredentials(token))
      .onSuccess(user -> {
        String userId = user.principal().getString("sub");

        if (userId == null || userId.isBlank()) {
          unauthorized(ctx, "Token missing userId claim");
          return;
        }

        ctx.put("userId", userId);
        ctx.next();
      })
      .onFailure(err -> unauthorized(ctx, "Invalid or expired token"));
  }

  private void unauthorized(RoutingContext ctx, String reason) {
    ctx.response()
      .setStatusCode(401)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", "Unauthorized: " + reason).encode());
  }
}
