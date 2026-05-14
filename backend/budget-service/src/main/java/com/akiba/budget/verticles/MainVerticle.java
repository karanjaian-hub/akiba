package com.akiba.budget.verticles;

import com.akiba.budget.consumers.PaymentCompletedConsumer;
import com.akiba.budget.handlers.BudgetCheckHandler;
import com.akiba.budget.handlers.GetBudgetsHandler;
import com.akiba.budget.handlers.UpsertBudgetHandler;
import com.akiba.budget.middleware.JwtMiddleware;
import com.akiba.budget.repositories.BudgetRepository;
import com.akiba.budget.services.BudgetCacheService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends VerticleBase {

    @Override
    public Future<?> start() {
        Pool           pool  = buildPool();
        RabbitMQClient mq    = buildRabbitMQClient();
        RedisAPI       redis = buildRedisApi();
        JWTAuth        jwt   = buildJwtAuth();

        BudgetRepository         budgetRepo   = new BudgetRepository(pool);
        BudgetCacheService       cacheService = new BudgetCacheService(redis);
        PaymentCompletedConsumer consumer     = new PaymentCompletedConsumer(mq, budgetRepo, cacheService);

        return vertx.deployVerticle(new SchemaVerticle(pool))
            .compose(id -> mq.start())
            .compose(v  -> consumer.start())
            .compose(v  -> startHttpServer(budgetRepo, cacheService, jwt))
            .onSuccess(v -> System.out.println("[BudgetService] Started on port " + servicePort()));
    }

    private Future<Void> startHttpServer(BudgetRepository budgetRepo, BudgetCacheService cacheService, JWTAuth jwt) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Health check is public — registered BEFORE the JWT middleware
        router.get("/budgets/health")
            .handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"budget-service\"}"));

        // All routes below this point require a valid JWT
        router.route().handler(new JwtMiddleware(jwt));

        router.get("/budgets")
            .handler(new GetBudgetsHandler(budgetRepo)::handle);
        router.post("/budgets")
            .handler(new UpsertBudgetHandler(budgetRepo)::handle);

        // overview BEFORE :category — Vert.x matches routes top-down
        router.get("/budgets/overview")
            .handler(new GetBudgetsHandler(budgetRepo)::handle);
        router.get("/budgets/:category/check")
            .handler(new BudgetCheckHandler(budgetRepo, cacheService)::handle);

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(servicePort())
            .mapEmpty();
    }

    private JWTAuth buildJwtAuth() {
        return JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer(System.getenv("JWT_SECRET"))));
    }

    private Pool buildPool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(System.getenv("DB_HOST"))
            .setPort(Integer.parseInt(System.getenv("DB_PORT")))
            .setDatabase(System.getenv("DB_NAME"))
            .setUser(System.getenv("DB_USER"))
            .setPassword(System.getenv("DB_PASS")
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new io.vertx.core.net.ClientSSLOptions().setTrustAll(true)));
        return PgBuilder.pool()
            .with(new PoolOptions().setMaxSize(10))
            .connectingTo(connectOptions)
            .using(vertx)
            .build();
    }

  private RabbitMQClient buildRabbitMQClient() {
    boolean useTls = System.getenv().getOrDefault("RABBITMQ_PORT", "5672").equals("5671");
    String uri = (useTls ? "amqps" : "amqp")
      + "://" + System.getenv().getOrDefault("RABBITMQ_USER", "guest")
      + ":" + System.getenv().getOrDefault("RABBITMQ_PASS", "guest")
      + "@" + System.getenv().getOrDefault("RABBITMQ_HOST", "rabbitmq")
      + ":" + System.getenv().getOrDefault("RABBITMQ_PORT", "5672")
      + "/" + System.getenv().getOrDefault("RABBITMQ_VHOST", "/");

    return RabbitMQClient.create(vertx,
      new RabbitMQOptions()
        .setUri(uri)
        .setTrustAll(useTls));
  }

  private RedisAPI buildRedisApi() {
    String redisHost     = System.getenv().getOrDefault("REDIS_HOST", "redis");
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
    return RedisAPI.api(Redis.createClient(vertx, redisOptions));
  }
    private int servicePort() {
        String port = System.getenv("SERVICE_PORT");
        return port != null ? Integer.parseInt(port) : 8086;
    }
}
