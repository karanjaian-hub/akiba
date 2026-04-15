package com.akiba.parsing.verticles;

import com.akiba.parsing.consumers.ParseConsumerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    return initDatabase()
      .compose(v -> deployConsumer())
      .compose(v -> deployHttp())
      .onSuccess(v -> System.out.println("[parsing-service] Started successfully on port "
        + config().getInteger("SERVICE_PORT", 8083)));
  }

  private Future<Void> initDatabase() {
    return vertx.deployVerticle(new DatabaseVerticle()).mapEmpty();
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
