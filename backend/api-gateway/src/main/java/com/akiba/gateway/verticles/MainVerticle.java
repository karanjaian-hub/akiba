package com.akiba.gateway.verticles;

import com.akiba.gateway.middleware.JwtMiddleware;
import com.akiba.gateway.middleware.RateLimitMiddleware;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

public class MainVerticle extends VerticleBase {

  private RedisAPI redis;
  private JWTAuth jwtAuth;
  private HttpClient httpClient;

  @Override
  public Future<?> start() {
    return connectRedis()
      .compose(v -> startHttpServer())
      .onSuccess(v -> System.out.println("[ApiGateway] ✅ Started on port " + servicePort()))
      .onFailure(err -> System.err.println("[ApiGateway] ❌ Startup failed: " + err.getMessage()));
  }

  // ─── Redis ────────────────────────────────────────────────────────────────

  private Future<Void> connectRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    return Redis.createClient(vertx, new RedisOptions()
        .setConnectionString("redis://" + redisHost + ":6379"))
      .connect()
      .compose(conn -> {
        redis = RedisAPI.api(conn);
        jwtAuth = createJwtAuth();
        httpClient = vertx.createHttpClient(new HttpClientOptions()
          .setConnectTimeout(5000));
        System.out.println("[ApiGateway] ✅ Redis connected");
        return Future.succeededFuture();
      });
  }

  // ─── HTTP Server ──────────────────────────────────────────────────────────

  private Future<Void> startHttpServer() {
    return vertx.createHttpServer()
      .requestHandler(buildRouter())
      .listen(servicePort())
      .mapEmpty();
  }

  private Router buildRouter() {
    Router router = Router.router(vertx);

    JwtMiddleware jwtMiddleware = new JwtMiddleware(jwtAuth, redis);
    RateLimitMiddleware rateLimitMiddleware = new RateLimitMiddleware(redis);

    // In Vert.x 5 CorsHandler.create() takes no args — origins added via allowedOrigin()
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
    router.get("/health").handler(this::handleHealth);

    // ─── Public Routes ────────────────────────────────────────────────────
    router.post("/auth/register").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/login").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/refresh").handler(ctx -> proxyTo(ctx, "auth-service", 8081));

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

  private void proxyTo(RoutingContext ctx, String service, int port) {
    long startTime = System.currentTimeMillis();
    String method = ctx.request().method().name();
    String path = ctx.request().uri();
    String userId = ctx.get("userId") != null ? ctx.get("userId") : "anonymous";

    httpClient.request(ctx.request().method(), port, service, path)
      .compose(req -> {
        ctx.request().headers().forEach(h -> req.putHeader(h.getKey(), h.getValue()));
        req.putHeader("X-User-Id", userId);
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
        upstreamRes.body().onSuccess(body -> ctx.response().end(body));
      })
      .onFailure(err -> {
        System.err.println("[ApiGateway] ❌ Proxy failed → " + service + ": " + err.getMessage());
        ctx.response()
          .setStatusCode(502)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "Service unavailable").encode());
      });
  }

  // ─── Health ───────────────────────────────────────────────────────────────

  private void handleHealth(RoutingContext ctx) {
    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject()
        .put("status", "UP")
        .put("service", "api-gateway")
        .encode());
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
  public Future<?> stop() throws Exception {
    if (httpClient != null) httpClient.close();
    System.out.println("[ApiGateway] 🛑 Stopped");
    return super.stop();
  }
}
