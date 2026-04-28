package com.akiba.payment.verticles;

import com.akiba.payment.config.PaymentConfig;
import com.akiba.payment.handlers.*;
import com.akiba.payment.repositories.PaymentRepository;
import com.akiba.payment.services.DarajaService;
import com.akiba.payment.services.PaymentService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.sqlclient.Pool;

import java.util.Set;


public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {

    vertx.deployVerticle(new SchemaVerticle())
      .compose(id -> connectInfrastructure())
      .compose(this::startHttpServer)
      .onSuccess(v -> {
        System.out.println("[payment-service] Started on port " + PaymentConfig.servicePort());
        startPromise.complete(); // ← tells Vert.x "I'm ready to serve traffic"
      })
      .onFailure(startPromise::fail); // ← tells Vert.x "abort, don't deploy me"
  }

  // Infrastructure setup

  private Future<InfrastructureComponents> connectInfrastructure() {
    Pool  db    = PaymentConfig.createPgPool(vertx);
    Redis redis = PaymentConfig.createRedisClient(vertx);

    RabbitMQOptions rmqOpts = new RabbitMQOptions()
      .setHost(PaymentConfig.rabbitmqHost())
      .setPort(5672)
      .setUser("guest")
      .setPassword("guest")
      .setReconnectAttempts(50)
      .setReconnectInterval(3000);

    RabbitMQClient rabbitMQ = RabbitMQClient.create(vertx, rmqOpts);

    return rabbitMQ.start()
      .map(v -> new InfrastructureComponents(db, redis, rabbitMQ))
      .onFailure(err -> System.err.println("[payment-service] RabbitMQ connection failed: " + err.getMessage()));
  }

  // HTTP server

  private Future<Void> startHttpServer(InfrastructureComponents infra) {
    Router router = buildRouter(infra);

    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(PaymentConfig.servicePort())
      .mapEmpty();
  }

  private Router buildRouter(InfrastructureComponents infra) {
    PaymentRepository repository = new PaymentRepository(infra.db);
    DarajaService     daraja     = new DarajaService(vertx, infra.redis);
    PaymentService    service    = new PaymentService(vertx, repository, daraja, infra.redis, infra.rabbitMQ);

    InitiatePaymentHandler initiateHandler  = new InitiatePaymentHandler(service, repository);
    DarajaCallbackHandler  callbackHandler  = new DarajaCallbackHandler(service);
    PaymentStatusHandler   statusHandler    = new PaymentStatusHandler(repository, infra.redis);
    RecipientsHandler      recipientsHandler = new RecipientsHandler(repository);

    Router router = Router.router(vertx);

    // Global middleware
    router.route().handler(CorsHandler.create()
      .addOrigin("*")  // tighten in production to your Expo app origin
      .allowedMethod(io.vertx.core.http.HttpMethod.GET)
      .allowedMethod(io.vertx.core.http.HttpMethod.POST)
      .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));

    router.route().handler(BodyHandler.create());

    router.post("/payments/callback").handler(callbackHandler::handle);

// Auth middleware — applies to all other /payments/* routes
    router.route("/payments/*").handler(ctx -> {
      String userIdHeader = ctx.request().getHeader("X-User-Id"); // set by API Gateway after JWT validation
      if (userIdHeader == null || userIdHeader.isBlank()) {
        ctx.response().setStatusCode(401)
          .end(new JsonObject().put("error", "Unauthorized").encode());
        return;
      }
      ctx.put("userId", userIdHeader);
      ctx.next();
    });

    // Routes
    router.post("/payments/initiate")            .handler(initiateHandler::handle);

    // Callback has NO JWT — Safaricom posts here directly
    router.post("/payments/callback")            .handler(callbackHandler::handle);

    router.get("/payments/history")              .handler(ctx -> {
      // Inner class hack — PaymentHistoryHandler is package-private; use directly here
      new com.akiba.payment.handlers.PaymentStatusHandler(repository, infra.redis); // placeholder
      // PLACEHOLDER: wire PaymentHistoryHandler here once it's a top-level public class
      ctx.response().setStatusCode(501).end(new JsonObject().put("error", "Not yet wired").encode());
    });

    router.get("/payments/status/:paymentId")    .handler(statusHandler::handle);
    router.get("/payments/recipients")           .handler(recipientsHandler::handleGet);
    router.put("/payments/recipients/:id")       .handler(recipientsHandler::handleUpdate);

    // Health endpoint — no auth required. Used by Docker and API Gateway
    router.get("/health").handler(ctx ->
      ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("status",  "UP")
          .put("service", "payment-service")
          .put("port",    PaymentConfig.servicePort())
          .encode())
    );

    return router;
  }

  // Helper record to pass infrastructure components around

  private record InfrastructureComponents(Pool db, Redis redis, RabbitMQClient rabbitMQ) {}
}
