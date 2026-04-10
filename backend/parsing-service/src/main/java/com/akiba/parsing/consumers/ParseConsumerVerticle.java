package com.akiba.parsing.consumers;

import com.akiba.parsing.models.ParsedTransaction;
import com.akiba.parsing.services.BankPdfParserService;
import com.akiba.parsing.services.CategoryService;
import com.akiba.parsing.services.GeminiClient;
import com.akiba.parsing.services.MpesaSmsParserService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;

import java.util.List;

/**
 * Listens on the 'parse.statement' queue.
 *
 * Each message follows this shape:
 * {
 *   "jobId":   "uuid",
 *   "userId":  "uuid",
 *   "type":    "MPESA_SMS" | "BANK_PDF",
 *   "content": "base64 string"
 * }
 *
 * We ONLY ack the message after fully successful processing.
 * On any failure, we publish to 'dead.letter' and then ack
 * (so the message doesn't replay infinitely with no consumer to fix it).
 */
public class ParseConsumerVerticle extends AbstractVerticle {

  private RabbitMQClient        rabbitMQ;
  private MpesaSmsParserService smsParser;
  private BankPdfParserService  pdfParser;
  private CategoryService       categoryService;

  @Override
  public void start(Promise<Void> startPromise) {
    GeminiClient gemini = new GeminiClient(vertx);
    smsParser       = new MpesaSmsParserService(gemini);
    pdfParser       = new BankPdfParserService(vertx, gemini);
    categoryService = new CategoryService(gemini);

    rabbitMQ = RabbitMQClient.create(vertx, buildRabbitMQOptions());

    rabbitMQ.start()
      .compose(v -> declareQueues())
      .compose(v -> startConsuming())
      .onSuccess(v -> {
        System.out.println("[ParseConsumerVerticle] Listening on 'parse.statement'");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private Future<Void> declareQueues() {
    // durable=true ensures queues survive RabbitMQ restarts
    return rabbitMQ.queueDeclare("parse.statement", true, false, false)
      .compose(v -> rabbitMQ.queueDeclare("transaction.save", true, false, false))
      .compose(v -> rabbitMQ.queueDeclare("dead.letter",      true, false, false))
      .mapEmpty();
  }

  private Future<Void> startConsuming() {
    return rabbitMQ.basicConsumer("parse.statement")
      .map(consumer -> {
        consumer.handler(message -> {
          JsonObject body = message.body().toJsonObject();
          String jobId    = body.getString("jobId", "unknown");

          processMessage(body)
            .onSuccess(v -> {
              // Only ack AFTER successfully publishing to transaction.save
              rabbitMQ.basicAck(message.envelope().getDeliveryTag(), false);
            })
            .onFailure(err -> {
              System.err.println("[ParseConsumerVerticle] Job " + jobId + " failed: " + err.getMessage());
              publishToDeadLetter(jobId, err.getMessage())
                .eventually(() -> {
                  // Ack even on failure — we've sent it to dead.letter for human review
                  rabbitMQ.basicAck(message.envelope().getDeliveryTag(), false);
                  return Future.succeededFuture();
                });
            });
        });
        return null;
      })
      .mapEmpty();
  }

  private Future<Void> processMessage(JsonObject messageBody) {
    String jobId   = messageBody.getString("jobId");
    String userId  = messageBody.getString("userId");
    String type    = messageBody.getString("type", "MPESA_SMS").toUpperCase();
    String content = messageBody.getString("content", "");

    return parseContent(type, content)
      .compose(transactions -> categoryService.categorize(transactions))
      .compose(categorized  -> publishToTransactionSave(jobId, userId, categorized));
  }

  // Route to the right parser based on job type
  private Future<List<ParsedTransaction>> parseContent(String type, String content) {
    return switch (type) {
      case "MPESA_SMS" -> smsParser.parse(content);
      case "BANK_PDF"  -> pdfParser.parse(content);
      default          -> Future.failedFuture("Unknown job type: " + type);
    };
  }

  private Future<Void> publishToTransactionSave(
    String jobId, String userId, List<ParsedTransaction> transactions) {

    // Wrap as an array so transaction-service can batch-insert efficiently
    JsonArray txArray = new JsonArray();
    transactions.forEach(tx -> txArray.add(tx.toJson().put("userId", userId)));

    JsonObject payload = new JsonObject()
      .put("jobId",        jobId)
      .put("userId",       userId)
      .put("transactions", txArray);

    return rabbitMQ.basicPublish("", "transaction.save", payload.toBuffer());
  }

  private Future<Void> publishToDeadLetter(String jobId, String errorMessage) {
    JsonObject payload = new JsonObject()
      .put("jobId",     jobId)
      .put("error",     errorMessage)
      .put("timestamp", System.currentTimeMillis());

    return rabbitMQ.basicPublish("", "dead.letter", payload.toBuffer());
  }

  private RabbitMQOptions buildRabbitMQOptions() {
    return new RabbitMQOptions()
      .setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"))
      .setPort(5672)
      .setUser("guest")
      .setPassword("guest")
      .setAutomaticRecoveryEnabled(true);  // Reconnect if RabbitMQ restarts
  }
}
