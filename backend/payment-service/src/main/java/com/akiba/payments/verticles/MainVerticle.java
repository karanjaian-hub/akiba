package com.akiba.payments.verticles;

import com.akiba.payments.handlers.PaymentHandler;
import com.akiba.payments.repositories.PaymentRepository;
import com.akiba.payments.services.DarajaService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Vert.x calls start() when deploying this verticle.
 *
 * Startup order matters:
 *   DB pool → Redis → RabbitMQ → Schema → HTTP server
 *
 * We use Future.compose() so each step only runs if the previous succeeded.
 * If anything fails, the whole verticle fails to start (which causes Docker to restart it).
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    JsonObject config = config(); // populated from env vars via docker-compose

    PgPool        pgPool   = buildPgPool(config);
    RedisAPI      redis    = buildRedis(config);
    RabbitMQClient rabbitMQ = buildRabbitMQ(config);
    WebClient     webClient = WebClient.create(vertx);
    String        budgetUrl = config.getString("BUDGET_SERVICE_URL", "http://budget-service:8086");

    // Deploy SchemaVerticle first, then wire HTTP when schema is ready
    vertx.deployVerticle(new SchemaVerticle(pgPool))
      .compose(v -> startHttpServer(pgPool, redis, rabbitMQ, webClient, budgetUrl, config))
      .onSuccess(v -> startPromise.complete())
      .onFailure(startPromise::fail);
  }

  // -------------------------------------------------------------------------
  // HTTP server & routing
  // -------------------------------------------------------------------------

  private Future<Void> startHttpServer(
      PgPool pgPool, RedisAPI redis, RabbitMQClient rabbitMQ,
      WebClient webClient, String budgetUrl, JsonObject config) {

    DarajaService     darajaService     = new DarajaService(webClient, redis, config);
    PaymentRepository paymentRepository = new PaymentRepository(pgPool);
    PaymentHandler    handler           = new PaymentHandler(
      darajaService, paymentRepository, webClient, redis, rabbitMQ, budgetUrl);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create()); // parse JSON bodies on all routes

    // Public (no JWT) — Daraja calls this from outside the cluster
    router.post("/payments/callback").handler(handler::handleCallback);

    // Health check — no auth needed (used by Docker healthcheck)
    router.get("/health").handler(handler::healthCheck);

    // JWT-protected routes — API gateway validates the token before forwarding here
    // We trust the X-User-Id / user context passed by the gateway
    router.post("/payments/initiate").handler(handler::initiatePayment);
    router.get("/payments/history").handler(handler::getPaymentHistory);
    router.get("/payments/status/:paymentId").handler(handler::getPaymentStatus);
    router.get("/payments/recipients").handler(handler::getRecipients);
    router.put("/payments/recipients/:id").handler(handler::updateRecipient);

    int port = Integer.parseInt(config.getString("SERVICE_PORT", "8085"));

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(server -> log.info("payment-service started on port {}", server.actualPort()))
      .mapEmpty();
  }

  // -------------------------------------------------------------------------
  // Infrastructure builders
  // -------------------------------------------------------------------------

  private PgPool buildPgPool(JsonObject config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(config.getString("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(config.getString("DB_PORT", "5432")))
      .setDatabase(config.getString("DB_NAME", "akiba_db"))
      .setUser(config.getString("DB_USER", "akiba"))
      .setPassword(config.getString("DB_PASS", "akiba_secret"));

    // Max 5 connections — each service gets its own pool slice
    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    return PgPool.pool(vertx, connectOptions, poolOptions);
  }

  private RedisAPI buildRedis(JsonObject config) {
    String redisHost = config.getString("REDIS_HOST", "redis");
    int    redisPort = Integer.parseInt(config.getString("REDIS_PORT", "6379"));

    Redis redisClient = Redis.createClient(vertx,
      new RedisOptions().setConnectionString("redis://" + redisHost + ":" + redisPort));

    return RedisAPI.api(redisClient);
  }

  private RabbitMQClient buildRabbitMQ(JsonObject config) {
    RabbitMQOptions options = new RabbitMQOptions()
      .setHost(config.getString("RABBITMQ_HOST", "rabbitmq"))
      .setPort(Integer.parseInt(config.getString("RABBITMQ_PORT", "5672")))
      .setUser(config.getString("RABBITMQ_USER", "guest"))
      .setPassword(config.getString("RABBITMQ_PASS", "guest"));

    RabbitMQClient client = RabbitMQClient.create(vertx, options);
    client.start(ar -> {
      if (ar.failed()) log.error("RabbitMQ connection failed", ar.cause());
    });
    return client;
  }
}
