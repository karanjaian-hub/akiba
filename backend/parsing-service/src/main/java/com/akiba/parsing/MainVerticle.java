package com.akiba.parsing;

import com.akiba.parsing.consumers.ParseConsumerVerticle;
import com.akiba.parsing.verticles.HttpVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * Entry point for the Parsing Service.
 *
 * Boot order matters here: database schema must exist before
 * the consumer starts processing messages that write to it.
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    initDatabase()
      .compose(v -> deployConsumer())
      .compose(v -> deployHttp())
      .onSuccess(v -> {
        System.out.println("[parsing-service] Started successfully on port "
          + config().getInteger("SERVICE_PORT", 8083));
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  // Runs schema migrations (CREATE TABLE IF NOT EXISTS) before anything else
  private Future<Void> initDatabase() {
    return vertx.deployVerticle(new com.akiba.parsing.verticles.DatabaseVerticle())
      .mapEmpty();
  }

  private Future<Void> deployConsumer() {
    return vertx.deployVerticle(
      ParseConsumerVerticle.class.getName(),
      new DeploymentOptions().setConfig(config())
    ).mapEmpty();
  }

  private Future<Void> deployHttp() {
    return vertx.deployVerticle(
      HttpVerticle.class.getName(),
      new DeploymentOptions().setConfig(config())
    ).mapEmpty();
  }
}
