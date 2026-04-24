package com.akiba.ai.verticles;

import com.akiba.ai.handlers.AiHandler;
import com.akiba.ai.providers.GeminiProvider;
import com.akiba.ai.repositories.AiRepository;
import com.akiba.ai.services.AiCacheService;
import com.akiba.ai.services.AiService;
import com.akiba.ai.services.FinancialContextService;
import com.akiba.ai.services.ReportGenerationConsumer;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
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
    String geminiKey = System.getenv("GEMINI_API_KEY");
    if (geminiKey == null || geminiKey.isBlank()) {
      return Future.failedFuture("GEMINI_API_KEY environment variable is not set");
    }

    int port = Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8084"));

    // ── Build infrastructure clients ──────────────────────────────────────
    Pool           pgPool    = buildPgPool();
    RedisAPI       redis     = buildRedis();
    RabbitMQClient rabbitMQ  = buildRabbitMQ();
    WebClient webClient = WebClient.create(vertx);

    // ── Build dependency graph ────────────────────────────────────────────
    GeminiProvider           gemini    = new GeminiProvider(webClient, geminiKey);
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
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"));

    // PgBuilder.pool() is the Vert.x 5 replacement for PgPool.pool().
    return PgBuilder.pool()
      .connectingTo(connectOptions)
      .with(new PoolOptions().setMaxSize(5))
      .using(vertx)
      .build();
  }

  private RedisAPI buildRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "redis");
    Redis redis = Redis.createClient(vertx, new RedisOptions()
      .setConnectionString("redis://" + redisHost + ":6379"));
    return RedisAPI.api(redis);
  }

  private RabbitMQClient buildRabbitMQ() {
    String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "rabbitmq");
    return RabbitMQClient.create(vertx, new RabbitMQOptions()
      .setHost(rabbitHost)
      .setPort(5672)
      .setUser("guest")
      .setPassword("guest"));
  }
}
