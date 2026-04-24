package com.akiba.notification.verticles;

import com.akiba.notification.config.AppConfig;
import com.akiba.notification.handlers.AlertHandler;
import com.akiba.notification.handlers.HealthCheckHandler;
import com.akiba.notification.repositories.PreferencesRepository;
import com.akiba.notification.services.PushNotificationService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.sqlclient.Pool;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    AppConfig config = AppConfig.from(config());

    Pool           pgPool    = AppConfig.buildPgPool(vertx, config);
    RabbitMQClient rabbit    = AppConfig.buildRabbitMQ(vertx, config);
    Redis          redisConn = Redis.createClient(vertx, "redis://" + config.redisHost() + ":6379");
    RedisAPI       redis     = RedisAPI.api(redisConn);
    WebClient      webClient = WebClient.create(vertx);

    AlertHandler            alertHandler = new AlertHandler(pgPool);
    PreferencesRepository   prefsRepo    = new PreferencesRepository(pgPool);
    PushNotificationService pushSvc      = new PushNotificationService(webClient);
    HealthCheckHandler      healthCheck  = new HealthCheckHandler(
      vertx, pgPool, rabbit, redis, "notification-service"
    );

    // Deploy schema first, then consumers + HTTP in parallel
    return vertx.deployVerticle(new SchemaVerticle(pgPool))
      .compose(v -> Future.all(
        vertx.deployVerticle(new RabbitMQConsumerVerticle(rabbit, alertHandler, prefsRepo, pushSvc)),
        vertx.deployVerticle(new HttpVerticle(alertHandler, prefsRepo, config.servicePort(), healthCheck))
      ))
      .onSuccess(v -> System.out.println("[NotificationService] Fully started on port " + config.servicePort()))
      .onFailure(err -> System.err.println("[NotificationService] Startup failed: " + err.getMessage()))
      .mapEmpty();
  }
}
