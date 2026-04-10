package com.akiba.budget;

import com.akiba.budget.consumers.PaymentCompletedConsumer;
import com.akiba.budget.handlers.BudgetCheckHandler;
import com.akiba.budget.handlers.GetBudgetsHandler;
import com.akiba.budget.handlers.UpsertBudgetHandler;
import com.akiba.budget.repositories.BudgetRepository;
import com.akiba.budget.services.BudgetCacheService;
import com.akiba.budget.verticles.SchemaVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    Pool           pgPool  = buildPgPool();
    RabbitMQClient mq      = buildRabbitMQClient();
    RedisAPI       redis   = buildRedisApi();

    BudgetRepository         budgetRepo   = new BudgetRepository(pgPool);
    BudgetCacheService       cacheService = new BudgetCacheService(redis);
    PaymentCompletedConsumer consumer     = new PaymentCompletedConsumer(mq, budgetRepo, cacheService);

    vertx.deployVerticle(new SchemaVerticle(pgPool))
      .compose(v -> mq.start())
      .compose(v -> consumer.start())
      .compose(v -> startHttpServer(budgetRepo, cacheService))
      .onSuccess(v -> {
        System.out.println("[BudgetService] Started on port " + servicePort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  // Returns Future<Void>, not Promise<Void>
  private Future<Void> startHttpServer(BudgetRepository budgetRepo, BudgetCacheService cacheService) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/budgets")
      .handler(new GetBudgetsHandler(budgetRepo)::handle);
    router.post("/budgets")
      .handler(new UpsertBudgetHandler(budgetRepo)::handle);
    router.get("/budgets/:category/check")
      .handler(new BudgetCheckHandler(budgetRepo, cacheService)::handle);
    router.get("/budgets/overview")
      .handler(new GetBudgetsHandler(budgetRepo)::handle);
    router.get("/health")
      .handler(ctx -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end("{\"status\":\"UP\",\"service\":\"budget-service\"}"));

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(servicePort())
      .mapEmpty(); // cleanly converts Future<HttpServer> → Future<Void>
  }

  private Pool buildPgPool() {
    PgConnectOptions opts = new PgConnectOptions()
      .setHost(System.getenv("DB_HOST"))
      .setPort(Integer.parseInt(System.getenv("DB_PORT")))
      .setDatabase(System.getenv("DB_NAME"))
      .setUser(System.getenv("DB_USER"))
      .setPassword(System.getenv("DB_PASS"));
    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(10))
      .connectingTo(opts)
      .using(vertx)
      .build();
  }

  private RabbitMQClient buildRabbitMQClient() {
    return RabbitMQClient.create(vertx,
      new RabbitMQOptions().setHost(System.getenv("RABBITMQ_HOST")));
  }

  private RedisAPI buildRedisApi() {
    Redis client = Redis.createClient(vertx,
      new RedisOptions().setConnectionString(
        "redis://" + System.getenv("REDIS_HOST") + ":6379"));
    return RedisAPI.api(client);
  }

  private int servicePort() {
    String port = System.getenv("SERVICE_PORT");
    return port != null ? Integer.parseInt(port) : 8086;
  }
}
