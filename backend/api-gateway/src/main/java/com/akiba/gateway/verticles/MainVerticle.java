package com.akiba.gateway.verticles;

import com.akiba.gateway.middleware.JwtMiddleware;
import com.akiba.gateway.middleware.RateLimitMiddleware;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;
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

  @Override
  public Future<?> start() {
    return connectRedis()
      .compose(v -> startHttpServer())
      .onSuccess(v -> System.out.println("[ApiGateway] ✅ Started on port " + servicePort()))
      .onFailure(err -> System.err.println("[ApiGateway] ❌ Startup failed: " + err.getMessage()));
  }

  private Future<Void> connectRedis() {
    String redisHost     = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    String redisPort     = System.getenv().getOrDefault("REDIS_PORT", "6379");
    String redisPassword = System.getenv().getOrDefault("REDIS_PASSWORD", "");
    String redisTls      = System.getenv().getOrDefault("REDIS_TLS", "false");

    String redisUrl = redisTls.equals("true")
      ? "rediss://default:" + redisPassword + "@" + redisHost + ":" + redisPort
      : "redis://" + redisHost + ":" + redisPort;

    RedisOptions redisOptions = new RedisOptions().setConnectionString(redisUrl);
    if (redisTls.equals("true")) {
      redisOptions.setNetClientOptions(
        new io.vertx.core.net.NetClientOptions()
          .setSsl(true)
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustAll(true)
      );
    }
    return Redis.createClient(vertx, redisOptions)
      .connect()
      .compose(conn -> {
        redis = RedisAPI.api(conn);
        jwtAuth = createJwtAuth();
        System.out.println("[ApiGateway] ✅ Redis connected");
        return Future.succeededFuture();
      });
  }

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

    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedMethod(HttpMethod.GET)
      .allowedMethod(HttpMethod.POST)
      .allowedMethod(HttpMethod.PUT)
      .allowedMethod(HttpMethod.DELETE)
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));

    router.route().handler(BodyHandler.create());

    router.get("/health").handler(this::handleHealth);

    // Public auth routes
    router.post("/auth/register").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/login").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/refresh").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/verify-phone").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/verify-email").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/forgot-password").handler(ctx -> proxyTo(ctx, "auth-service", 8081));
    router.post("/auth/reset-password").handler(ctx -> proxyTo(ctx, "auth-service", 8081));

    // Daraja callback (no JWT)
    router.post("/payments/callback").handler(ctx -> proxyTo(ctx, "payment-service", 8085));

    // Protected routes
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

  private String serviceUrl(String name, int defaultPort) {
    String envKey = name.toUpperCase().replace("-", "_") + "_URL";
    String url = System.getenv(envKey);
    if (url != null && !url.isEmpty()) {
      return url;
    }
    return "http://" + name + ":" + defaultPort;
  }

  private void proxyTo(RoutingContext ctx, String service, int port) {
    long startTime = System.currentTimeMillis();
    String path   = ctx.request().uri();
    String userId = ctx.get("userId") != null ? ctx.get("userId") : "anonymous";

    String baseUrl = serviceUrl(service, port);
    boolean useSSL = baseUrl.startsWith("https");
    int targetPort = useSSL ? 443 : port;
    String hostWithPort = baseUrl
      .replace("https://", "")
      .replace("http://", "")
      .split("/")[0];
    String host = hostWithPort.contains(":") ? hostWithPort.split(":")[0] : hostWithPort;
    if (hostWithPort.contains(":")) {
      targetPort = Integer.parseInt(hostWithPort.split(":")[1]);
    }

    HttpClientOptions opts = new HttpClientOptions()
      .setConnectTimeout(60000)
      .setIdleTimeout(60)
      .setSsl(useSSL)
      .setTrustAll(useSSL);

    HttpClient client = vertx.createHttpClient(opts);

    client.request(ctx.request().method(), targetPort, host, path)
      .compose(req -> {
        ctx.request().headers().forEach(h -> {
          String key = h.getKey().toLowerCase();
          if (!key.equals("host") && !key.equals("connection") && !key.equals("transfer-encoding")) {
            req.putHeader(h.getKey(), h.getValue());
          }
        });
        req.putHeader("Host", host);
        req.putHeader("X-User-Id",   userId);
        req.putHeader("X-User-Role", ctx.get("role") != null ? ctx.get("role") : "");
        Buffer body = ctx.body().buffer();
        if (body != null && body.length() > 0) {
          return req.send(body);
        } else {
          return req.send();
        }
      })
      .onSuccess(upstreamRes -> {
        long ms = System.currentTimeMillis() - startTime;
        System.out.printf("[ApiGateway] %s %s → %s (%dms) userId=%s%n",
          ctx.request().method().name(), path, service, ms, userId);
        ctx.response().setStatusCode(upstreamRes.statusCode());
        upstreamRes.headers().forEach(h ->
          ctx.response().putHeader(h.getKey(), h.getValue()));
        upstreamRes.body()
          .onSuccess(respBody -> {
            if (respBody != null && respBody.length() > 0) {
              ctx.response().end(respBody);
            } else {
              ctx.response().end();
            }
          })
          .onFailure(err -> ctx.response().end());
      })
      .onFailure(err -> {
        System.err.println("[ApiGateway] ❌ Proxy failed → " + service + ": " + err.getMessage());
        ctx.response()
          .setStatusCode(502)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "Service unavailable").encode());
      })
      .eventually(() -> client.close());
  }

  private void handleHealth(RoutingContext ctx) {
    ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject()
        .put("status", "UP")
        .put("service", "api-gateway")
        .encode());
  }

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
    System.out.println("[ApiGateway] ✅ Stopped");
    return super.stop();
  }
}
