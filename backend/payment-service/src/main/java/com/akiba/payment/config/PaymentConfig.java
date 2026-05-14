package com.akiba.payment.config;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;


public class PaymentConfig {

    public static Pool createPgPool(Vertx vertx) {
    PgConnectOptions connect = new PgConnectOptions()
      .setHost(env("DB_HOST", "localhost"))
      .setPort(Integer.parseInt(env("DB_PORT", "5432")))
      .setDatabase(env("DB_NAME", "akiba_db"))
      .setUser(env("DB_USER", "akiba"))
      .setPassword(env("DB_PASS", "akiba_secret")
      .setSslMode(SslMode.REQUIRE)
      .setSslOptions(new io.vertx.core.net.ClientSSLOptions().setTrustAll(true)));

    PoolOptions pool = new PoolOptions().setMaxSize(10);

    return PgBuilder.pool()
      .with(pool)
      .connectingTo(connect)
      .using(vertx)
      .build();
  }

  public static Redis createRedisClient(Vertx vertx) {
    String redisTls      = env("REDIS_TLS", "false");
    String redisPassword = env("REDIS_PASSWORD", "");
    String redisHost     = env("REDIS_HOST", "localhost");
    String redisPort     = env("REDIS_PORT", "6379");

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
    return Redis.createClient(vertx, redisOptions);
  }

  // Daraja saf configs
  public static String darajaConsumerKey()    { return require("DARAJA_CONSUMER_KEY"); }
  public static String darajaConsumerSecret() { return require("DARAJA_CONSUMER_SECRET"); }
  public static String darajaShortcode()      { return require("DARAJA_SHORTCODE"); }
  public static String darajaPasskey()        { return require("DARAJA_PASSKEY"); }
  public static String darajaCallbackUrl()    { return require("DARAJA_CALLBACK_URL"); }

  // Service config
  public static int    servicePort()          { return Integer.parseInt(env("SERVICE_PORT", "8085")); }
  public static String budgetServiceUrl()     { return env("BUDGET_SERVICE_URL", "http://budget-service:8086"); }
  public static String jwtSecret()        { return require("JWT_SECRET"); }
  public static String rabbitmqHost()         { return env("RABBITMQ_HOST", "rabbitmq"); }

  // Helpers
  private static String env(String key, String fallback) {
    String val = System.getenv(key);
    return (val != null && !val.isBlank()) ? val : fallback;
  }

  //Crash early if a required variable is missing
  private static String require(String key) {
    String val = System.getenv(key);
    if (val == null || val.isBlank()) {
      throw new IllegalStateException("Required env var missing: " + key);
    }
    return val;
  }
}
