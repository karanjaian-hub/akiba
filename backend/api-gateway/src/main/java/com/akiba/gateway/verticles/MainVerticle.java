package com.akiba.gateway.verticles;

import com.akiba.gateway.middleware.JwtMiddleware;
import com.akiba.gateway.middleware.RateLimitMiddleware;
import com.akiba.gateway.middleware.RbacMiddleware;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

// API Gateway — single entry point for all mobile requests.
// Handles JWT verification, RBAC, rate limiting, and proxying

public class MainVerticle extends AbstractVerticle {

  private RedisAPI redis;
  private JWTAuth jwtAuth;
  private HttpClient httpClient;

  @Override
  public void start(Promise<Void> startPromise) {
    connectRedis()
      .compose(v -> startHttpServer())
      .onSuccess(v -> {
        System.out.println("[ApiGateway] ✅ Started on port " + servicePort());
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[ApiGateway] ❌ Startup failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  // ─── Redis ────────────────────────────────────────────────────────────────

  private Future<Void> connectRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    return Redis.createClient(vertx, new RedisOptions()
      .setConnectionString("redis://" + redisHost + ":6379"))
      .connect()
      .compose(conn -> {
        redis   = RedisAPI.api(conn);
        jwtAuth = createJwtAuth();
        httpClient = vertx.createHttpClient(new HttpClientOptions()
          .setConnectTimeout(5000));
        System.out.println("[ApiGateway] ✅ Redis connected");
        return Future.succeededFuture();
      });
  }

  // ─── HTTP Server ──────────────────────────────────────────────────────────

  private Future<Void> startHttpServer() {
    Router router = buildRouter();
    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(servicePort())
      .mapEmpty();
  }

  private Router buildRouter() {
    Router router = Router.router(vertx);

    // Middleware instances
    JwtMiddleware jwtMiddleware       = new JwtMiddleware(jwtAuth, redis);
    RbacMiddleware rbacMiddleware      = new RbacMiddleware();
    RateLimitMiddleware rateLimitMiddleware = new RateLimitMiddleware(redis);

    // CORS for React Native
    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedMethod(HttpMethod.GET)
      .allowedMethod(HttpMethod.POST)
      .allowedMethod(HttpMethod.PUT)
      .allowedMethod(HttpMethod.DELETE)
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));

    router.route().handler(BodyHandler.create());

    // ─── Health ───────────────────────────────────────────────────────────
    router.get("/health").handler(ctx -> checkDownstreamHealth(ctx));

    // ─── Public Routes (no JWT needed) ───────────────────────────────────
    router.post("/auth/register")     .handler(ctx -> proxyTo(ctx, "auth-service",     8081));
    router.post("/auth/verify-phone") .handler(ctx -> proxyTo(ctx, "auth-service",     8081));
    router.post("/auth/verify-email") .handler(ctx -> proxyTo(ctx, "auth-service",     8081));
    router.post("/auth/login")        .handler(ctx -> proxyTo(ctx, "auth-service",     8081));
    router.post("/auth/refresh")      .handler(ctx -> proxyTo(ctx, "auth-service",     8081));

    // ─── Protected Routes ─────────────────────────────────────────────────
    router.route("/auth/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "auth-service", 8081));

    router.route("/transactions/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "transaction-service", 8082));

    router.route("/parse/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "parsing-service", 8083));

    router.route("/ai/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "ai-service", 8084));

    // Payment routes get rate limiting — max 5 per minute per user
    router.route("/payments/*")
      .handler(jwtMiddleware::handle)
      .handler(rateLimitMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "payment-service", 8085));

    router.route("/budgets/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "budget-service", 8086));

    router.route("/savings/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "savings-service", 8087));

    router.route("/notifications/*")
      .handler(jwtMiddleware::handle)
      .handler(ctx -> proxyTo(ctx, "notification-service", 8088));

    return router;
  }

  // ─── Proxy ────────────────────────────────────────────────────────────────

  private void proxyTo(io.vertx.ext.web.RoutingContext ctx, String service, int port) {
    long startTime = System.currentTimeMillis();
    String method  = ctx.request().method().name();
    String path    = ctx.request().uri();
    String userId  = ctx.get("userId") != null ? ctx.get("userId") : "anonymous";

    httpClient.request(ctx.request().method(),
      port, service, path)
      .compose(req -> {
        // Forward all original headers to the downstream service
        ctx.request().headers().forEach(h -> req.putHeader(h.getKey(), h.getValue()));
        // Pass userId downstream so services don't need to decode JWT themselves
        req.putHeader("X-User-Id",  userId);
        req.putHeader("X-User-Role", ctx.get("role") != null ? ctx.get("role") : "");
        return req.send(ctx.body().buffer());
      })
      .onSuccess(upstreamRes -> {
        long ms = System.currentTimeMillis() - startTime;
        System.out.printf("[ApiGateway] %s %s → %s (%dms) userId=%s%n",
          method, path, service, ms, userId);
        ctx.response().setStatusCode(upstreamRes.statusCode());
        upstreamRes.headers().forEach(h ->
          ctx.response().putHeader(h.getKey(), h.getValue()));
        upstreamRes.body().onSuccess(body ->
          ctx.response().end(body));
      })
      .onFailure(err -> {
        System.err.println("[ApiGateway] ❌ Proxy failed → " + service + ": " + err.getMessage());
        ctx.response().setStatusCode(502)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Service unavailable\"}");
      });
  }

  // ─── Health Check ─────────────────────────────────────────────────────────

  private void checkDownstreamHealth(io.vertx.ext.web.RoutingContext ctx) {
    io.vertx.core.json.JsonObject status = new io.vertx.core.json.JsonObject()
      .put("status",  "UP")
      .put("service", "api-gateway")
      .put("downstream", new io.vertx.core.json.JsonObject()
        .put("auth-service",         "http://auth-service:8081/health")
        .put("transaction-service",  "http://transaction-service:8082/health")
        .put("parsing-service",      "http://parsing-service:8083/health")
        .put("ai-service",           "http://ai-service:8084/health")
        .put("payment-service",      "http://payment-service:8085/health")
        .put("budget-service",       "http://budget-service:8086/health")
        .put("savings-service",      "http://savings-service:8087/health")
        .put("notification-service", "http://notification-service:8088/health"));

    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(status.encode());
  }

  // ─── JWT Setup ────────────────────────────────────────────────────────────

  private JWTAuth createJwtAuth() {
    String secret = System.getenv().getOrDefault("JWT_SECRET", "akiba_dev_secret");
    return JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(secret)));
  }

  private int servicePort() {
    return Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8080"));
  }

  @Override
  public void stop() {
    if (httpClient != null) httpClient.close();
    System.out.println("[ApiGateway] 🛑 Stopped");
  }
}
