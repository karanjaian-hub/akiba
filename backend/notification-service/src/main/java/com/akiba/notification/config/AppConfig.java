package com.akiba.notification.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class AppConfig {

  private final String dbHost;
  private final int    dbPort;
  private final String dbName;
  private final String dbUser;
  private final String dbPass;
  private final String rabbitHost;
  private final String redisHost;
  private final int    servicePort;

  private AppConfig(
    String dbHost, int dbPort, String dbName, String dbUser, String dbPass,
    String rabbitHost, String redisHost, int servicePort
  ) {
    this.dbHost      = dbHost;
    this.dbPort      = dbPort;
    this.dbName      = dbName;
    this.dbUser      = dbUser;
    this.dbPass      = dbPass;
    this.rabbitHost  = rabbitHost;
    this.redisHost   = redisHost;
    this.servicePort = servicePort;
  }

  public static AppConfig from(JsonObject vertxConfig) {
    return new AppConfig(
      env("DB_HOST",       "localhost"),
      Integer.parseInt(env("DB_PORT", "5432")),
      env("DB_NAME",       "akiba_db"),
      env("DB_USER",       "akiba"),
      env("DB_PASS",       "akiba_secret"),
      env("RABBITMQ_HOST", "localhost"),
      env("REDIS_HOST",    "localhost"),
      Integer.parseInt(env("SERVICE_PORT", "8088"))
    );
  }

  // Vert.x 5: PgPool.pool() is gone — use PgBuilder instead
  // Returns Pool (from io.vertx.sqlclient) not PgPool
  public static Pool buildPgPool(Vertx vertx, AppConfig config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(config.dbHost)
      .setPort(config.dbPort)
      .setDatabase(config.dbName)
      .setUser(config.dbUser)
      .setPassword(config.dbPass);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    return PgBuilder.pool()
      .with(poolOptions)
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  public static RabbitMQClient buildRabbitMQ(Vertx vertx, AppConfig config) {
    RabbitMQOptions options = new RabbitMQOptions()
      .setHost(config.rabbitHost)
      .setPort(5672)
      .setUser("guest")
      .setPassword("guest")
      .setVirtualHost("/")
      .setReconnectAttempts(10)
      .setReconnectInterval(1000);

    return RabbitMQClient.create(vertx, options);
  }

  public String redisHost()   { return redisHost; }
  public int    servicePort() { return servicePort; }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return (value != null && !value.isBlank()) ? value : defaultValue;
  }
}
