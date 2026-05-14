package com.akiba.ai;

import com.akiba.ai.verticles.MainVerticle;
import com.akiba.ai.verticles.SchemaVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AiServiceApplication {

  private static final Logger log = LoggerFactory.getLogger(AiServiceApplication.class);

  public static void main(String[] args) {

    Vertx vertx = Vertx.builder()
      .with(new VertxOptions()
        .setWorkerPoolSize(20)
        .setEventLoopPoolSize(2 * Runtime.getRuntime().availableProcessors()))
      .build();

    SchemaVerticle schemaVerticle = new SchemaVerticle();

    vertx.deployVerticle(schemaVerticle)
      .compose(id -> vertx.deployVerticle(new MainVerticle()))
      .onSuccess(id -> log.info("All verticles deployed successfully."))
      .onFailure(err -> {
        log.error("Startup failed: {}", err.getMessage());
        vertx.close();
        System.exit(1);
      });
  }
}
