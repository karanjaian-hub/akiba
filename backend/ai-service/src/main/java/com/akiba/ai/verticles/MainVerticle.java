package com.akiba.ai.verticles;

import com.akiba.ai.handlers.AiHandler;
import com.akiba.ai.providers.GroqProvider;
import com.akiba.ai.repositories.AiRepository;
import com.akiba.ai.services.AiCacheService;
import com.akiba.ai.services.AiService;
import com.akiba.ai.services.FinancialContextService;
import com.akiba.ai.consumer.ReportGenerationConsumer;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainVerticle wires all dependencies and opens the HTTP server.
 *
 * Extends VerticleBase (Vert.x 5). The key difference from AbstractVerticle:
 *   - start() returns Future<?> instead of taking a Promise<Void> parameter.
 *   - We chain everything into one Future pipeline — cleaner and harder to
 *     accidentally forget to complete the promise.
 *
 * Vert.x 5 specific changes applied here:
 *   - PgBuilder.pool() replaces PgPool.pool() (deprecated in v5).
 *   - RabbitMQClient.start() must be awaited before basicConsumer().
 *   - WebClient.create() takes WebClientOptions in v5 for proper config.
 */
public class MainVerticle extends VerticleBase {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    String groqKey = System.getenv("GROQ_API_KEY");
    if (groqKey == null || groqKey.isBlank()) {
      return Future.failedFuture("GROQ_API_KEY environment variable is not set");
    }

    int port = Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8084"));

    // ── Build infrastructure clients ──────────────────────────────────────
    Pool           pgPool    = buildPgPool();
    RedisAPI       redis     = buildRedis();
    RabbitMQClient rabbitMQ  = buildRabbitMQ();
    WebClient webClient = WebClient.create(vertx);

    // ── Build dependency graph ────────────────────────────────────────────
    GroqProvider             gemini    = new GroqProvider(webClient, groqKey);
    AiCacheService           cache     = new AiCacheService(redis);
    FinancialContextService  context   = new FinancialContextService(webClient);
    AiRepository             repo      = new AiRepository(pgPool);
    AiService                aiService = new AiService(gemini, cache, context, repo);
    AiHandler                handler   = new AiHandler(aiService);
    ReportGenerationConsumer consumer  = new ReportGenerationConsumer(rabbitMQ, gemini, repo, webClient);

    // ── Wire HTTP routes ──────────────────────────────────────────────────
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.post("/ai/chat")                .handler(handler::chat);
    router.get("/ai/conversations")        .handler(handler::getConversations);
    router.get("/ai/reports/:month/:year") .handler(handler::getReport);
    router.post("/ai/insights")            .handler(handler::quickInsight);
    router.get("/health")                  .handler(ctx -> ctx.response().end("OK"));

    // ── Startup sequence ──────────────────────────────────────────────────
    // In Vert.x 5, RabbitMQClient must be explicitly started before use.
    // We chain: start rabbitMQ → register consumer → open HTTP port.
    return rabbitMQ.start()
      .compose(v -> consumer.start())
      .compose(v -> vertx.createHttpServer()
        .requestHandler(router)
        .listen(port))
      .onSuccess(server -> log.info("AI Service started on port {}", port));
  }

  private Pool buildPgPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"))
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new io.vertx.core.net.ClientSSLOptions().setTrustAll(true));

    // PgBuilder.pool() is the Vert.x 5 replacement for PgPool.pool().
    return PgBuilder.pool()
      .connectingTo(connectOptions)
      .with(new PoolOptions().setMaxSize(5))
      .using(vertx)
      .build();
  }

  private RedisAPI buildRedis() {
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

  private RabbitMQClient buildRabbitMQ() {
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
}
