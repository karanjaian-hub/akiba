package com.akiba.parsing.verticles;

import com.akiba.parsing.handlers.ParseHandler;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;

public class HttpVerticle extends VerticleBase {

  @Override
  public Future<?> start() {
    int port = config().getInteger("SERVICE_PORT", 8083);

    return startRabbitMQ()
      .map(this::buildRouter)
      .compose(router -> vertx.createHttpServer()
        .requestHandler(router)
        .listen(port))
      .onSuccess(server ->
        System.out.println("[HttpVerticle] HTTP server listening on port " + port));
  }

  private Future<RabbitMQClient> startRabbitMQ() {
    RabbitMQClient client = RabbitMQClient.create(vertx, new RabbitMQOptions()
      .setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"))
      .setPort(5672)
      .setAutomaticRecoveryEnabled(true));

    return client.start().map(v -> client);
  }

  private Router buildRouter(RabbitMQClient rabbitMQ) {
    ParseHandler parseHandler = new ParseHandler(rabbitMQ);
    JWTAuth      jwtAuth      = buildJwtAuth();

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setBodyLimit(10 * 1024 * 1024));

    router.get("/health").handler(parseHandler::handleHealth);

    router.route("/*").handler(JWTAuthHandler.create(jwtAuth));
    router.post("/mpesa").handler(parseHandler::handleMpesaParse);
    router.post("/bank").handler(parseHandler::handleBankParse);

    return router;
  }

  private JWTAuth buildJwtAuth() {
    String jwtSecret = System.getenv().getOrDefault("JWT_SECRET", "change-me-in-production");
    return JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(jwtSecret)));
  }
}
