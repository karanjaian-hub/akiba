package com.akiba.ai;

import com.akiba.ai.handlers.AiHandler;
import com.akiba.ai.providers.GeminiProvider;
import com.akiba.ai.repositories.AiRepository;
import com.akiba.ai.services.AiCacheService;
import com.akiba.ai.services.AiService;
import com.akiba.ai.services.FinancialContextService;
import com.akiba.ai.services.ReportGenerationConsumer;
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
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainVerticle is the entry point for the AI Service.
 *
 * Responsibility: wire all dependencies together and start the HTTP server.
 * It does NOT contain any business logic — that belongs in the service layer.
 *
 * Startup order:
 *   1. Connect to Redis (async — must complete before proceeding).
 *   2. Connect to Postgres, RabbitMQ.
 *   3. Build all service / handler instances (dependency injection by hand).
 *   4. Register HTTP routes.
 *   5. Start RabbitMQ consumer.
 *   6. Open the HTTP server.
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    int    port      = Integer.parseInt(System.getenv().getOrDefault("SERVICE_PORT", "8084"));
    String geminiKey = System.getenv("GEMINI_API_KEY");

    if (geminiKey == null || geminiKey.isBlank()) {
      startPromise.fail("GEMINI_API_KEY environment variable is not set");
      return;
    }

    Pool           pool      = buildPool();
    RabbitMQClient rabbitMQ  = buildRabbitMQ();
    WebClient      webClient = WebClient.create(vertx);

    // Connect Redis first — everything else depends on it being ready
    buildRedis()
      .onFailure(startPromise::fail)
      .onSuccess(redis -> {

        // ── Build dependency graph ──────────────────────────────────────
        GeminiProvider           gemini    = new GeminiProvider(webClient, geminiKey);
        AiCacheService           cache     = new AiCacheService(redis);
        FinancialContextService  context   = new FinancialContextService(webClient);
        AiRepository             repo      = new AiRepository(pool);
        AiService                aiService = new AiService(gemini, cache, context, repo);
        AiHandler                handler   = new AiHandler(aiService);
        ReportGenerationConsumer consumer  = new ReportGenerationConsumer(rabbitMQ, gemini, repo, webClient);

        // ── Wire HTTP routes ────────────────────────────────────────────
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.post("/ai/chat")                .handler(handler::chat);
        router.get("/ai/conversations")        .handler(handler::getConversations);
        router.get("/ai/reports/:month/:year") .handler(handler::getReport);
        router.post("/ai/insights")            .handler(handler::quickInsight);
        router.get("/health")                  .handler(ctx -> ctx.response().end("OK"));

        // ── Start consumer then HTTP server ─────────────────────────────
        consumer.start()
          .compose(v -> vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
          )
          .onSuccess(server -> {
            log.info("AI Service started on port {}", port);
            startPromise.complete();
          })
          .onFailure(startPromise::fail);
      });
  }

  private Pool buildPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "postgres"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(5))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  private Future<RedisAPI> buildRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "redis");
    Redis redis = Redis.createClient(vertx, new RedisOptions()
      .setConnectionString("redis://" + redisHost + ":6379"));
    return redis.connect().map(conn -> RedisAPI.api(redis));
  }

  private RabbitMQClient buildRabbitMQ() {
    String rabbitHost = System.getenv().getOrDefault("RABBITMQ_HOST", "rabbitmq");
    return RabbitMQClient.create(vertx, new RabbitMQOptions()
      .setHost(rabbitHost)
      .setPort(5672)
      .setUser("guest")
      .setPassword("guest")
    );
  }
}
