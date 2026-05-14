package com.akiba.payment.verticles;

import com.akiba.payment.config.PaymentConfig;
import com.akiba.payment.handlers.DarajaCallbackHandler;
import com.akiba.payment.handlers.InitiatePaymentHandler;
import com.akiba.payment.handlers.PaymentHistoryHandler;
import com.akiba.payment.handlers.PaymentStatusHandler;
import com.akiba.payment.handlers.RecipientsHandler;
import com.akiba.payment.repositories.PaymentRepository;
import com.akiba.payment.services.DarajaService;
import com.akiba.payment.services.PaymentService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.redis.client.Redis;
import io.vertx.sqlclient.Pool;

import java.util.List;
import java.util.UUID;


public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    // Each step is chained — if any fail, startPromise.fail() is called and
    // Vert.x will refuse to mark this verticle as deployed.
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
      .setReconnectAttempts(10)
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
    PaymentHistoryHandler  historyHandler    = new PaymentHistoryHandler(repository);
    PaymentStatusHandler   statusHandler     = new PaymentStatusHandler(repository, infra.redis);
    RecipientsHandler      recipientsHandler = new RecipientsHandler(repository);

    Router router = Router.router(vertx);

    JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(PaymentConfig.jwtSecret())));

    // Cors config
    router.route().handler(CorsHandler.create()
      .addOrigin("*")
      .allowedMethod(io.vertx.core.http.HttpMethod.GET)
      .allowedMethod(io.vertx.core.http.HttpMethod.POST)
      .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));

    router.route().handler(BodyHandler.create());

    // Apply JWT validation to all /payments/* routes.
    router.post("/payments/callback").handler(callbackHandler::handle);

    router.route("/payments/*").handler(JWTAuthHandler.create(jwtAuth));

    // Extract userId from the validated JWT claims and put it on the context... every handler can read it with ctx.get("userId").
    router.route("/payments/*").handler(ctx -> {
      String userId = ctx.user().principal().getString("sub");;
      if (userId == null || userId.isBlank()) {
        ctx.response().setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("error", "Unauthorized").encode());
        return;
      }
      ctx.put("userId", userId);
      ctx.next();
    });

    // Routes/ endpoints
    router.post("/payments/initiate")             .handler(initiateHandler::handle);
    router.get("/payments/history")               .handler(historyHandler::handle);

    router.get("/payments/status/:paymentId")     .handler(statusHandler::handle);
    router.get("/payments/recipients")            .handler(recipientsHandler::handleGet);
    router.put("/payments/recipients/:id")        .handler(recipientsHandler::handleUpdate);

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

  private int parseIntParam(List<String> values, int defaultVal) {
    if (values == null || values.isEmpty()) return defaultVal;
    try { return Integer.parseInt(values.get(0)); }
    catch (NumberFormatException e) { return defaultVal; }
  }

  // Helper record to pass infrastructure components around
  private record InfrastructureComponents(Pool db, Redis redis, RabbitMQClient rabbitMQ) {}
}
