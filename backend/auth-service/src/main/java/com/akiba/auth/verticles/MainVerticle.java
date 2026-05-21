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
import io.vertx.pgclient.SslMode;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;


public class MainVerticle extends VerticleBase {

  private Pool pool;
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

  // Deploy the Schema
  private Future<Void> deploySchemaVerticle() {
    return vertx.deployVerticle(new SchemaVerticle()).mapEmpty();
  }

  // connecting with the Postgres Pool
  private Future<Void> connectPostgres() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "localhost"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"))
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new io.vertx.core.net.ClientSSLOptions().setTrustAll(true));

    pool = PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(10))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
    System.out.println("[AuthService] ✅ Postgres pool ready");
    return Future.succeededFuture();
  }

  // Connecting Redis
  private Future<Void> connectRedis() {
    String redisHost     = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    String redisPort     = System.getenv().getOrDefault("REDIS_PORT", "6379");
    String redisPassword = System.getenv().getOrDefault("REDIS_PASSWORD", "");
    String redisTls      = System.getenv().getOrDefault("REDIS_TLS", "false");

    String redisUrl = redisTls.equals("true")
      ? "rediss://default:" + redisPassword + "@" + redisHost + ":" + redisPort
      : "redis://" + redisHost + ":" + redisPort;

    RedisOptions redisOptions = new RedisOptions()
      .setConnectionString(redisUrl);

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
        System.out.println("[AuthService] ✅ Redis connected");
        return Future.succeededFuture();
      });
  }

  // Mail Service
  private Future<Void> initMailService() {
    mailService = new MailService(vertx);
    System.out.println("[AuthService] ✅ Mail service ready");
    return Future.succeededFuture();
  }

  // Start HTTP Server & Router
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

    // Authentication Handlers
    RegisterHandler           registerHandler           = new RegisterHandler(pool, redis, mailService);
    VerifyEmailHandler        verifyEmailHandler        = new VerifyEmailHandler(pool, redis);
    ResendVerificationHandler resendVerificationHandler = new ResendVerificationHandler(pool, redis, mailService);
    LoginHandler              loginHandler              = new LoginHandler(pool, redis, jwtAuth);
    RefreshTokenHandler       refreshTokenHandler       = new RefreshTokenHandler(pool, redis, jwtAuth);
    LogoutHandler             logoutHandler             = new LogoutHandler(pool, redis);
    ForgotPasswordHandler     forgotPasswordHandler     = new ForgotPasswordHandler(pool, redis, mailService);
    ResetpasswordHandler      resetPasswordHandler      = new ResetpasswordHandler(pool, redis);

    // Authentication Public Routes
    router.post("/auth/register")             .handler(registerHandler::handle);
    router.post("/auth/verify-email")         .handler(verifyEmailHandler::handle);
    router.post("/auth/resend-verification")  .handler(resendVerificationHandler::handle);
    router.post("/auth/login")                .handler(loginHandler::handle);
    router.post("/auth/refresh")              .handler(refreshTokenHandler::handle);
    router.post("/auth/forgot-password")      .handler(forgotPasswordHandler::handle);
    router.post("/auth/reset-password")       .handler(resetPasswordHandler::handle);

    // Protected Routes (JWT is required)
    router.post("/auth/logout")
      .handler(jwtMiddleware())
      .handler(logoutHandler::handle);

    router.put("/auth/profile")
      .handler(jwtMiddleware())
      .handler(ctx -> ctx.response()
        .setStatusCode(501)
        .end("{\"error\":\"Not implemented yet\"}"));

    // Admin Routes
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

    router.get("/auth/dev/activate/:email").handler(ctx -> {
      String email = ctx.pathParam("email");
      pool.preparedQuery("UPDATE auth.users SET status='ACTIVE' WHERE email=$1")
        .execute(io.vertx.sqlclient.Tuple.of(email))
        .onSuccess(r -> ctx.response().end("{\"updated\":" + r.rowCount() + "}"))
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });

    //  Health Check
    router.get("/health").handler(ctx -> ctx.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json")
      .end("{\"status\":\"UP\",\"service\":\"auth-service\"}"));

    return router;
  }

  // JWT Middleware

  private io.vertx.ext.web.handler.JWTAuthHandler jwtMiddleware() {
    return io.vertx.ext.web.handler.JWTAuthHandler.create(jwtAuth);
  }

  // Admin-Only Middleware

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

  // JWT Auth Setup
  private JWTAuth createJwtAuth() {
    String secret = System.getenv().getOrDefault("JWT_SECRET", "akiba_dev_secret");
    return JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(secret)));
  }

  // Helpers
  private int servicePort() {
    return Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8081"));
  }

  @Override
  public Future<Void> stop() {
    if (pool != null) pool.close();
    System.out.println("[AuthService] 🛑 Stopped");
    return Future.succeededFuture();
  }
}
