package com.akiba.savings.handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the Bearer JWT on every protected route.
 *
 * WHY executeBlocking?
 * JWT verification is CPU-bound crypto (HMAC-SHA256).
 * Running it on the event loop would stall every other request on that thread.
 * executeBlocking() hands it off to a worker thread pool so the event loop stays free.
 */
public class JwtAuthHandler {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthHandler.class);

  private final JWTAuth jwtAuth;
  private final Vertx   vertx;

  public JwtAuthHandler(Vertx vertx) {
    this.vertx = vertx;

    // Match auth-service exactly: System.getenv() + HS256 + setBuffer(secret)
    String secret = System.getenv().getOrDefault("JWT_SECRET", "akiba_dev_secret");

    this.jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(secret)));
  }

  public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      replyUnauthorized(ctx, "Missing or malformed Authorization header");
      return;
    }

    String token = authHeader.substring(7);

    // executeBlocking: runs crypto on a worker thread, resumes on event loop when done
    vertx.executeBlocking(() -> {
      var result = new java.util.concurrent.CompletableFuture<String>();
      // Vert.x 5: authenticate() takes TokenCredentials, not JsonObject
      jwtAuth.authenticate(new TokenCredentials(token))
        .onSuccess(user -> result.complete(user.subject()))
        .onFailure(err -> result.completeExceptionally(new Exception(err.getMessage())));
      return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }).onSuccess(userId -> {
      ctx.put("userId", userId);
      ctx.next();
    }).onFailure(err -> {
      log.warn("JWT validation failed: {}", err.getMessage());
      replyUnauthorized(ctx, "Invalid or expired token");
    });
  }

  private void replyUnauthorized(RoutingContext ctx, String message) {
    ctx.response()
      .setStatusCode(401)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
