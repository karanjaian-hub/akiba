package com.akiba.payments.verticles;

import com.akiba.payments.handlers.PaymentHandler;
import com.akiba.payments.repositories.PaymentRepository;
import com.akiba.payments.services.DarajaService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    JsonObject config = config();

    Pool          pool      = buildPool(config);
    RedisAPI      redis     = buildRedis(config);
    RabbitMQClient rabbitMQ = buildRabbitMQ(config);
    WebClient     webClient = WebClient.create(vertx);
    String        budgetUrl = config.getString("BUDGET_SERVICE_URL", "http://budget-service:8086");

    return vertx.deployVerticle(new SchemaVerticle(pool))
      .compose(v -> startHttpServer(pool, redis, rabbitMQ, webClient, budgetUrl, config));
  }

  private Future<Void> startHttpServer(
    Pool pool, RedisAPI redis, RabbitMQClient rabbitMQ,
    WebClient webClient, String budgetUrl, JsonObject config) {

    DarajaService     darajaService     = new DarajaService(webClient, redis, config);
    PaymentRepository paymentRepository = new PaymentRepository(pool);
    PaymentHandler    handler           = new PaymentHandler(
      darajaService, paymentRepository, webClient, redis, rabbitMQ, budgetUrl);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/payments/callback").handler(handler::handleCallback);
    router.get("/health").handler(handler::healthCheck);
    router.post("/payments/initiate").handler(handler::initiatePayment);
    router.get("/payments/history").handler(handler::getPaymentHistory);
    router.get("/payments/status/:paymentId").handler(handler::getPaymentStatus);
    router.get("/payments/recipients").handler(handler::getRecipients);
    router.put("/payments/recipients/:id").handler(handler::updateRecipient);

    int port = Integer.parseInt(config.getString("SERVICE_PORT", "8085"));

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(s -> log.info("payment-service started on port {}", s.actualPort()))
      .mapEmpty();
  }

  // -------------------------------------------------------------------------
  // Infrastructure builders
  // -------------------------------------------------------------------------

  private Pool buildPool(JsonObject config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(config.getString("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(config.getString("DB_PORT", "5432")))
      .setDatabase(config.getString("DB_NAME", "akiba_db"))
      .setUser(config.getString("DB_USER", "akiba"))
      .setPassword(config.getString("DB_PASS", "akiba_secret"));

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    // Vert.x 5: PgPool.pool() is removed — use PgBuilder
    return PgBuilder.pool()
      .with(poolOptions)
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  private RedisAPI buildRedis(JsonObject config) {
    String host = config.getString("REDIS_HOST", "redis");
    int    port = Integer.parseInt(config.getString("REDIS_PORT", "6379"));

    Redis client = Redis.createClient(vertx,
      new RedisOptions().setConnectionString("redis://" + host + ":" + port));

    return RedisAPI.api(client);
  }

  private RabbitMQClient buildRabbitMQ(JsonObject config) {
    RabbitMQOptions options = new RabbitMQOptions()
      .setHost(config.getString("RABBITMQ_HOST", "rabbitmq"))
      .setPort(Integer.parseInt(config.getString("RABBITMQ_PORT", "5672")))
      .setUser(config.getString("RABBITMQ_USER", "guest"))
      .setPassword(config.getString("RABBITMQ_PASS", "guest"));

    RabbitMQClient client = RabbitMQClient.create(vertx, options);
    client.start()
      .onFailure(err -> log.error("RabbitMQ connection failed", err));
    return client;
  }
}
