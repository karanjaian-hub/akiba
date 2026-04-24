package com.akiba.transaction.verticles;

import com.akiba.transaction.consumers.TransactionSaveConsumer;
import com.akiba.transaction.handlers.*;
import com.akiba.transaction.repositories.TransactionRepository;
import com.akiba.transaction.services.AnomalyDetectionService;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends VerticleBase {

  @Override
  public Future<?> start() {

    Pool           pgPool = buildPgPool();
    RabbitMQClient mq     = buildRabbitMQClient();

    TransactionRepository   txRepo         = new TransactionRepository(pgPool);
    AnomalyDetectionService anomalyService = new AnomalyDetectionService(pgPool);
    TransactionSaveConsumer consumer       = new TransactionSaveConsumer(mq, txRepo, anomalyService);

    return vertx.deployVerticle(new SchemaVerticle(pgPool))
      .compose(id -> mq.start())
      .compose(v  -> consumer.start())
      .compose(v  -> startHttpServer(txRepo))
      .onSuccess(v -> System.out.println("[TransactionService] Started on port " + servicePort()));
  }

  private Future<Void> startHttpServer(TransactionRepository txRepo) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/transactions")              .handler(new GetTransactionsHandler(txRepo)::handle);
    router.get("/transactions/summary")      .handler(new GetSummaryHandler(txRepo)::handle);
    router.get("/transactions/top-merchants").handler(new GetTopMerchantsHandler(txRepo)::handle);
    router.get("/transactions/:id")          .handler(new GetTransactionByIdHandler(txRepo)::handle);
    router.post("/transactions")             .handler(new CreateTransactionHandler(txRepo)::handle);
    router.put("/transactions/:id/category") .handler(new UpdateCategoryHandler(txRepo)::handle);

    router.get("/health").handler(ctx -> ctx.response()
      .putHeader("Content-Type", "application/json")
      .end("{\"status\":\"UP\",\"service\":\"transaction-service\"}"));

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(servicePort())
      .mapEmpty();
  }

  private Pool buildPgPool() {
    PgConnectOptions opts = new PgConnectOptions()
      .setHost(System.getenv("DB_HOST"))
      .setPort(Integer.parseInt(System.getenv("DB_PORT")))
      .setDatabase(System.getenv("DB_NAME"))
      .setUser(System.getenv("DB_USER"))
      .setPassword(System.getenv("DB_PASS"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(10))
      .connectingTo(opts)
      .using(vertx)
      .build();
  }

  private RabbitMQClient buildRabbitMQClient() {
    return RabbitMQClient.create(vertx,
      new RabbitMQOptions()
        .setHost(System.getenv("RABBITMQ_HOST"))
        .setReconnectAttempts(10)
        .setReconnectInterval(3000L));
  }

  private int servicePort() {
    String port = System.getenv("SERVICE_PORT");
    return port != null ? Integer.parseInt(port) : 8082;
  }
}
