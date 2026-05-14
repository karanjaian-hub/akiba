package com.akiba.savings.handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the Bearer JWT on every protected route.
 *
 * WHY executeBlocking?
 * JWT verification involves:
 *   - Base64 decoding the token
 *   - HMAC-SHA256 or RSA signature verification (CPU-bound crypto)
 *   - String parsing and comparison
 *
 * All of that is blocking/CPU work. Running it on the Vert.x event loop
 * would stall every other request on that thread. executeBlocking() hands
 * it off to a worker thread pool so the event loop stays free.
 */
public class JwtAuthHandler {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthHandler.class);

  private final JWTAuth jwtAuth;
  private final Vertx   vertx;

  public JwtAuthHandler(Vertx vertx, String jwtSecret) {
    this.vertx = vertx;

    // Build a symmetric (HMAC-SHA256) JWT verifier from the shared secret.
    // If your auth-service issues RS256 tokens, swap this for a PEM public key.
    this.jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(jwtSecret)));
  }

  /**
   * Call this as middleware on every protected route group:
   *   router.route("/savings/*").handler(jwtAuthHandler::handle);
   */
  public void handle(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      replyUnauthorized(ctx, "Missing or malformed Authorization header");
      return;
    }

    String token = authHeader.substring(7); // strip "Bearer "

    // executeBlocking: runs the crypto verification on a worker thread,
    // then resumes on the event loop when done — non-blocking for the event loop
    vertx.executeBlocking(() -> {
      // This lambda runs on a worker thread — safe to do CPU-bound work here
      return verifyToken(token);
    }).onSuccess(userId -> {
      // Back on the event loop — attach userId to context and continue the chain
      ctx.put("userId", userId);
      ctx.next();
    }).onFailure(err -> {
      log.warn("JWT validation failed: {}", err.getMessage());
      replyUnauthorized(ctx, "Invalid or expired token");
    });
  }

  /**
   * Verifies the token and returns the subject (userId).
   * This runs inside executeBlocking so blocking is safe here.
   */
  private String verifyToken(String token) throws Exception {
    // JWTAuth.authenticate is itself async but we're calling it synchronously
    // inside executeBlocking — the thread can block here without hurting the event loop
    var result = new java.util.concurrent.CompletableFuture<String>();

    jwtAuth.authenticate((Credentials) new JsonObject().put("token", token))
      .onSuccess(user -> result.complete(user.subject()))
      .onFailure(err -> result.completeExceptionally(new Exception(err.getMessage())));

    // Block this worker thread until authentication completes (fine — it's a worker thread)
    return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
  }

  private void replyUnauthorized(RoutingContext ctx, String message) {
    ctx.response()
      .setStatusCode(401)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
