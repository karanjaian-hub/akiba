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

    // Ensure upload temp directory exists
    vertx.fileSystem().mkdirs("/tmp/akiba-uploads");

    return startRabbitMQ()
      .map(this::buildRouter)
      .compose(router -> vertx.createHttpServer()
        .requestHandler(router)
        .listen(port))
      .onSuccess(server ->
        System.out.println("[HttpVerticle] HTTP server listening on port " + port));
  }

  private Future<RabbitMQClient> startRabbitMQ() {
    boolean useTls = System.getenv().getOrDefault("RABBITMQ_PORT", "5672").equals("5671");
    String uri = (useTls ? "amqps" : "amqp")
      + "://" + System.getenv().getOrDefault("RABBITMQ_USER", "guest")
      + ":" + System.getenv().getOrDefault("RABBITMQ_PASS", "guest")
      + "@" + System.getenv().getOrDefault("RABBITMQ_HOST", "rabbitmq")
      + ":" + System.getenv().getOrDefault("RABBITMQ_PORT", "5672")
      + "/" + System.getenv().getOrDefault("RABBITMQ_VHOST", "/");

    RabbitMQClient client = RabbitMQClient.create(vertx,
      new RabbitMQOptions()
        .setUri(uri)
        .setTrustAll(useTls)
        .setAutomaticRecoveryEnabled(true));

    return client.start()
      .compose(v -> client.queueDeclare("parse.statement", true, false, false))
      .map(v -> client);
  }

  private Router buildRouter(RabbitMQClient rabbitMQ) {
    ParseHandler parseHandler = new ParseHandler(rabbitMQ, vertx);
    JWTAuth      jwtAuth      = buildJwtAuth();

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create()
      .setBodyLimit(10 * 1024 * 1024)
      .setUploadsDirectory("/tmp/akiba-uploads")
      .setDeleteUploadedFilesOnEnd(false));

    router.get("/health").handler(parseHandler::handleHealth);

    router.route("/parse/*").handler(JWTAuthHandler.create(jwtAuth));
    router.post("/parse/mpesa").handler(parseHandler::handleMpesaParse);
    router.post("/parse/bank").handler(parseHandler::handleBankParse);

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
