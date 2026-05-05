package com.akiba.parsing.handlers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;

import java.util.Base64;
import java.util.UUID;

public class ParseHandler {

  private final RabbitMQClient rabbitMQ;
  private final Vertx          vertx;

  public ParseHandler(RabbitMQClient rabbitMQ, Vertx vertx) {
    this.rabbitMQ = rabbitMQ;
    this.vertx    = vertx;
  }

  public void handleMpesaParse(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null || body.getString("smsText", "").isBlank()) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "smsText is required").encode());
      return;
    }

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

  public void handleBankParse(RoutingContext ctx) {
    FileUpload fileUpload = ctx.fileUploads().stream()
      .filter(f -> f.name().equals("pdf"))
      .findFirst()
      .orElse(null);

    if (fileUpload == null) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "PDF file is required (multipart field: pdf)").encode());
      return;
    }

    String userId = ctx.user().subject();
    String jobId  = UUID.randomUUID().toString();

    vertx.fileSystem().readFile(fileUpload.uploadedFileName())
      .map(buffer -> Base64.getEncoder().encodeToString(buffer.getBytes()))
      .compose(base64Pdf -> {
        JsonObject message = new JsonObject()
          .put("jobId",   jobId)
          .put("userId",  userId)
          .put("type",    "BANK_PDF")
          .put("content", base64Pdf);

        return rabbitMQ.basicPublish("", "parse.statement", message.toBuffer());
      })
      .onSuccess(v -> {
        vertx.fileSystem().delete(fileUpload.uploadedFileName());
        ctx.response().setStatusCode(202)
          .end(new JsonObject()
            .put("jobId",   jobId)
            .put("status",  "QUEUED")
            .put("message", "Bank PDF parse job queued successfully")
            .encode());
      })
      .onFailure(err -> sendInternalError(ctx, "Failed to queue bank PDF job", err));
  }

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
