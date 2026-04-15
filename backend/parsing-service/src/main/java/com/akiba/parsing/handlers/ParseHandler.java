package com.akiba.parsing.handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;

import java.util.UUID;

public class ParseHandler {

  private final RabbitMQClient rabbitMQ;

  public ParseHandler(RabbitMQClient rabbitMQ) {
    this.rabbitMQ = rabbitMQ;
  }

  public void handleMpesaParse(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null || body.getString("smsText", "").isBlank()) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "smsText is required").encode());
      return;
    }

    // userId comes from the JWT, injected by api-gateway
    String userId  = ctx.user().subject();
    String jobId   = UUID.randomUUID().toString();
    String smsText = body.getString("smsText");

    JsonObject message = new JsonObject()
      .put("jobId",   jobId)
      .put("userId",  userId)
      .put("type",    "MPESA_SMS")
      .put("content", smsText);

    rabbitMQ.basicPublish("", "parse.statement", message.toBuffer())
      .onSuccess(v -> ctx.response().setStatusCode(202)
        .end(new JsonObject()
          .put("jobId",   jobId)
          .put("status",  "QUEUED")
          .put("message", "Parsing job queued successfully")
          .encode()))
      .onFailure(err -> sendInternalError(ctx, "Failed to queue parse job", err));
  }

  /**
   * POST /parse/bank
   * Body: { "pdf": "base64encodedPDFbytes" }
   */
  public void handleBankParse(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null || body.getString("pdf", "").isBlank()) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "pdf (base64) is required").encode());
      return;
    }

    String userId = ctx.user().subject();
    String jobId  = UUID.randomUUID().toString();
    String pdf    = body.getString("pdf");

    JsonObject message = new JsonObject()
      .put("jobId",   jobId)
      .put("userId",  userId)
      .put("type",    "BANK_PDF")
      .put("content", pdf);

    rabbitMQ.basicPublish("", "parse.statement", message.toBuffer())
      .onSuccess(v -> ctx.response().setStatusCode(202)
        .end(new JsonObject()
          .put("jobId",   jobId)
          .put("status",  "QUEUED")
          .put("message", "Bank PDF parse job queued successfully")
          .encode()))
      .onFailure(err -> sendInternalError(ctx, "Failed to queue bank PDF job", err));
  }

  /** GET /health — used by Docker HEALTHCHECK and load balancers */
  public void handleHealth(RoutingContext ctx) {
    ctx.response().setStatusCode(200)
      .end(new JsonObject()
        .put("status",  "UP")
        .put("service", "parsing-service")
        .encode());
  }

  private void sendInternalError(RoutingContext ctx, String message, Throwable cause) {
    System.err.println("[ParseHandler] " + message + ": " + cause.getMessage());
    ctx.response().setStatusCode(500)
      .end(new JsonObject().put("error", message).encode());
  }
}
