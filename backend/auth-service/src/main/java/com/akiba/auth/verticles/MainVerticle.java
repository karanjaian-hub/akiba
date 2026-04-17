package com.akiba.auth.verticles;

import com.akiba.auth.handlers.*;
import com.akiba.auth.services.MailService;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VerticleBase;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;


public class MainVerticle extends VerticleBase {

  private Pool pgPool;
  private RedisAPI redis;
  private JWTAuth jwtAuth;
  private MailService mailService;

  @Override
  public Future<Void> start() {
    return deploySchemaVerticle()
      .compose(v -> connectPostgres())
      .compose(v -> connectRedis())
      .compose(v -> initMailService())
      .compose(v -> startHttpServer())
      .onSuccess(v -> System.out.println("[AuthService] ✅ Started on port " + servicePort()));
  }

  // Step 1: Deploy Schema

  private Future<Void> deploySchemaVerticle() {
    return vertx.deployVerticle(new SchemaVerticle()).mapEmpty();
  }

  // Step 2: Postgres Pool

  private Future<Void> connectPostgres() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "localhost"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"));

    pgPool = PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(10))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
    System.out.println("[AuthService] ✅ Postgres pool ready");
    return Future.succeededFuture();
  }

  // Step 3: Redis

  private Future<Void> connectRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    RedisOptions redisOptions = new RedisOptions()
      .setConnectionString("redis://" + redisHost + ":6379");

    return Redis.createClient(vertx, redisOptions)
      .connect()
      .compose(conn -> {
        redis = RedisAPI.api(conn);
        jwtAuth = createJwtAuth();
        System.out.println("[AuthService] ✅ Redis connected");
        return Future.<Void>succeededFuture();
      });
  }

  // Step 4: Mail Service

  private Future<Void> initMailService() {
    mailService = new MailService(vertx);
    System.out.println("[AuthService] ✅ Mail service ready");
    return Future.succeededFuture();
  }

  // Step 5: HTTP Server & Router

  private Future<Void> startHttpServer() {
    Router router = buildRouter();
    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(servicePort())
      .mapEmpty();
  }

  private Router buildRouter() {
    Router router = Router.router(vertx);

    router.route().handler(
      CorsHandler.create()
        .addOrigin("*")
        .allowedMethod(io.vertx.core.http.HttpMethod.GET)
        .allowedMethod(io.vertx.core.http.HttpMethod.POST)
        .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
        .allowedHeader("Content-Type")
        .allowedHeader("Authorization")
    );

    router.route().handler(BodyHandler.create());

    // ─── Handlers ─────────────────────────────────────────────────────────
    RegisterHandler           registerHandler           = new RegisterHandler(pgPool, redis, mailService);
    VerifyEmailHandler        verifyEmailHandler        = new VerifyEmailHandler(pgPool, redis);
    ResendVerificationHandler resendVerificationHandler = new ResendVerificationHandler(pgPool, redis, mailService);
    LoginHandler              loginHandler              = new LoginHandler(pgPool, redis, jwtAuth);
    RefreshTokenHandler       refreshTokenHandler       = new RefreshTokenHandler(pgPool, redis, jwtAuth);
    LogoutHandler             logoutHandler             = new LogoutHandler(pgPool, redis);
    ForgotPasswordHandler     forgotPasswordHandler     = new ForgotPasswordHandler(pgPool, redis, mailService);
    ResetpasswordHandler      resetPasswordHandler      = new ResetpasswordHandler(pgPool, redis);

    // ─── Public Routes ────────────────────────────────────────────────────
    router.post("/auth/register")             .handler(registerHandler::handle);
    router.post("/auth/verify-email")         .handler(verifyEmailHandler::handle);
    router.post("/auth/resend-verification")  .handler(resendVerificationHandler::handle);
    router.post("/auth/login")                .handler(loginHandler::handle);
    router.post("/auth/refresh")              .handler(refreshTokenHandler::handle);
    router.post("/auth/forgot-password")      .handler(forgotPasswordHandler::handle);
    router.post("/auth/reset-password")       .handler(resetPasswordHandler::handle);

    // ─── Protected Routes (JWT required) ──────────────────────────────────
    router.post("/auth/logout")
      .handler(jwtMiddleware())
      .handler(logoutHandler::handle);

    router.put("/auth/profile")
      .handler(jwtMiddleware())
      .handler(ctx -> ctx.response()
        .setStatusCode(501)
        .end("{\"error\":\"Not implemented yet\"}"));

    // ─── Admin Routes ─────────────────────────────────────────────────────
    router.get("/auth/users")
      .handler(jwtMiddleware())
      .handler(adminOnly())
      .handler(ctx -> ctx.response()
        .setStatusCode(501)
        .end("{\"error\":\"Not implemented yet\"}"));

    router.put("/auth/users/:id/deactivate")
      .handler(jwtMiddleware())
      .handler(adminOnly())
      .handler(ctx -> ctx.response()
        .setStatusCode(501)
        .end("{\"error\":\"Not implemented yet\"}"));

    // ─── Health Check ─────────────────────────────────────────────────────
    router.get("/health").handler(ctx -> ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end("{\"status\":\"UP\",\"service\":\"auth-service\"}"));

    return router;
  }

  // ─── JWT Middleware ───────────────────────────────────────────────────────

  private io.vertx.ext.web.handler.JWTAuthHandler jwtMiddleware() {
    return io.vertx.ext.web.handler.JWTAuthHandler.create(jwtAuth);
  }

  // ─── Admin-Only Middleware ────────────────────────────────────────────────

  private Handler<RoutingContext> adminOnly() {
    return ctx -> {
      String role = ctx.user().principal().getString("role");
      if (!"ROLE_ADMIN".equals(role)) {
        ctx.response()
          .setStatusCode(403)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Forbidden — admin access required\"}");
        return;
      }
      ctx.next();
    };
  }

  // ─── JWT Auth Setup ───────────────────────────────────────────────────────

  private JWTAuth createJwtAuth() {
    String secret = System.getenv().getOrDefault("JWT_SECRET", "akiba_dev_secret");
    return JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(secret)));
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private int servicePort() {
    return Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8081"));
  }

  @Override
  public Future<Void> stop() {
    if (pgPool != null) pgPool.close();
    System.out.println("[AuthService] 🛑 Stopped");
    return Future.succeededFuture();
  }
}
