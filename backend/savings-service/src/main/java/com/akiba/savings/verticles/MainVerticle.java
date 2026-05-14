package com.akiba.savings.verticles;

import com.akiba.savings.consumers.ContributionConsumer;
import com.akiba.savings.handlers.JwtAuthHandler;
import com.akiba.savings.handlers.SavingsHandler;
import com.akiba.savings.repositories.SavingsRepository;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    JsonObject config = config();

    Pool           pool     = buildPool(config);
    RedisAPI       redis    = buildRedis(config);
    RabbitMQClient rabbitMQ = buildRabbitMQ(config);

    SavingsRepository repository = new SavingsRepository(pool);

    return vertx.deployVerticle(new SchemaVerticle(pool))
      .compose(v -> vertx.deployVerticle(new ContributionConsumer(rabbitMQ, repository, redis)))
      .compose(v -> startHttpServer(repository, redis, rabbitMQ, config));
  }

  private Future<Void> startHttpServer(
    SavingsRepository repository, RedisAPI redis,
    RabbitMQClient rabbitMQ, JsonObject config) {

    SavingsHandler handler = new SavingsHandler(repository, redis, rabbitMQ);

    // JWT secret must match what auth-service uses to sign tokens
    String jwtSecret = config.getString("JWT_SECRET", "change-me-in-production");
    JwtAuthHandler jwtAuth = new JwtAuthHandler(vertx, jwtSecret);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Public — no JWT needed
    router.get("/health").handler(handler::healthCheck);

    // Protected — JWT middleware runs first on all /savings/* routes,
    // then the specific handler runs via ctx.next() inside JwtAuthHandler
    router.route("/savings/*").handler(jwtAuth::handle);
    router.get("/savings/goals").handler(handler::getGoals);
    router.post("/savings/goals").handler(handler::createGoal);
    router.put("/savings/goals/:id").handler(handler::updateGoal);
    router.delete("/savings/goals/:id").handler(handler::archiveGoal);
    router.post("/savings/goals/:id/contribute").handler(handler::addManualContribution);
    router.get("/savings/goals/:id/history").handler(handler::getContributionHistory);

    int port = Integer.parseInt(config.getString("SERVICE_PORT", "8087"));

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(s -> log.info("savings-service started on port {}", s.actualPort()))
      .mapEmpty();
  }

  private Pool buildPool(JsonObject config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(config.getString("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(config.getString("DB_PORT", "5432")))
      .setDatabase(config.getString("DB_NAME", "akiba_db"))
      .setUser(config.getString("DB_USER", "akiba"))
      .setPassword(config.getString("DB_PASS", "akiba_secret"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(5))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }
  private RedisAPI buildRedis(JsonObject config) {
    String host     = config.getString("REDIS_HOST", "redis");
    String port     = config.getString("REDIS_PORT", "6379");
    String password = config.getString("REDIS_PASSWORD", "");
    String tls      = config.getString("REDIS_TLS", "false");

    String redisUrl = tls.equals("true")
      ? "rediss://default:" + password + "@" + host + ":" + port
      : "redis://" + host + ":" + port;

    RedisOptions redisOptions = new RedisOptions().setConnectionString(redisUrl);
    if (tls.equals("true")) {
      redisOptions.setNetClientOptions(
        new io.vertx.core.net.NetClientOptions()
          .setSsl(true)
          .setHostnameVerificationAlgorithm("HTTPS")
          .setTrustAll(true)
      );
    }
    return RedisAPI.api(Redis.createClient(vertx, redisOptions));
  }
  private RabbitMQClient buildRabbitMQ(JsonObject config) {
    RabbitMQClient client = RabbitMQClient.create(vertx, new RabbitMQOptions()
      .setHost(config.getString("RABBITMQ_HOST", "rabbitmq"))
      .setPort(Integer.parseInt(config.getString("RABBITMQ_PORT", "5672")))
      .setUser(config.getString("RABBITMQ_USER", "guest"))
      .setPassword(config.getString("RABBITMQ_PASS", "guest"))
      .setVirtualHost(config.getString("RABBITMQ_VHOST", "/"))
      .setSsl(config.getString("RABBITMQ_PORT", "5672").equals("5671")));
    client.start()
      .onFailure(err -> log.error("RabbitMQ connection failed", err));
    return client;
  }
}
