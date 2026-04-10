package com.akiba.transaction;

import com.akiba.transaction.consumers.TransactionSaveConsumer;
import com.akiba.transaction.handlers.*;
import com.akiba.transaction.repositories.TransactionRepository;
import com.akiba.transaction.services.AnomalyDetectionService;
import com.akiba.transaction.verticles.SchemaVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    Pool pool         = buildPool();
    RabbitMQClient mq = buildRabbitMQClient();

    // Wire up dependencies manually — no DI framework needed at this scale
    TransactionRepository   txRepo         = new TransactionRepository(pool);
    AnomalyDetectionService anomalyService = new AnomalyDetectionService(pool);
    TransactionSaveConsumer consumer       = new TransactionSaveConsumer(mq, txRepo, anomalyService);

    // Startup chain: schema → connect to MQ → start consumer → start HTTP server
    vertx.deployVerticle(new SchemaVerticle(pool))
      .compose(v -> mq.start())
      .compose(v -> consumer.start())
      .compose(v -> startHttpServer(txRepo))
      .onSuccess(v -> {
        System.out.println("[TransactionService] Started on port " + servicePort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private Future<Void> startHttpServer(TransactionRepository txRepo) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // All routes require JWT — the API Gateway enforces this before requests arrive here
    router.get("/transactions")              .handler(new GetTransactionsHandler(txRepo)::handle);
    router.get("/transactions/summary")      .handler(new GetSummaryHandler(txRepo)::handle);
    router.get("/transactions/top-merchants").handler(new GetTopMerchantsHandler(txRepo)::handle);
    router.get("/transactions/:id")          .handler(new GetTransactionByIdHandler(txRepo)::handle);
    router.post("/transactions")             .handler(new CreateTransactionHandler(txRepo)::handle);
    router.put("/transactions/:id/category") .handler(new UpdateCategoryHandler(txRepo)::handle);
    router.get("/health")                    .handler(ctx -> ctx.response().end("{\"status\":\"UP\"}"));

    Promise<Void> promise = Promise.promise();
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(servicePort())
      .onSuccess(s -> promise.complete())
      .onFailure(promise::fail);

    return promise.future();
  }

  private Pool buildPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv("DB_HOST"))
      .setPort(Integer.parseInt(System.getenv("DB_PORT")))
      .setDatabase(System.getenv("DB_NAME"))
      .setUser(System.getenv("DB_USER"))
      .setPassword(System.getenv("DB_PASS"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(10))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  private RabbitMQClient buildRabbitMQClient() {
    return RabbitMQClient.create(vertx,
      new RabbitMQOptions().setHost(System.getenv("RABBITMQ_HOST")));
  }

  private int servicePort() {
    String port = System.getenv("SERVICE_PORT");
    return port != null ? Integer.parseInt(port) : 8082;
  }
}
