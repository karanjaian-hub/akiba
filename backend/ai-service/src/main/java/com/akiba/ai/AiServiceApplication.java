package com.akiba.ai;

import com.akiba.ai.verticles.MainVerticle;
import com.akiba.ai.verticles.SchemaVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application boot — Vert.x 5 style.
 *
 * Changes from v4:
 *   - Vertx.builder().build() replaces Vertx.vertx() (v5 builder pattern).
 *   - No manual PgPool here — SchemaVerticle builds its own pool internally
 *     using its VerticleBase-provided vertx instance. Cleaner ownership.
 *   - Deploy order: SchemaVerticle (tables) → MainVerticle (HTTP + consumers).
 *     If schema fails, we exit with code 1 so Docker restart policy kicks in.
 */
public class AiServiceApplication {

  private static final Logger log = LoggerFactory.getLogger(AiServiceApplication.class);

  public static void main(String[] args) {
    // Vert.x 5: use builder instead of Vertx.vertx().
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
