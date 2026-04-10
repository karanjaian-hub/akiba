package com.akiba.transaction.consumers;

import com.akiba.transaction.repositories.TransactionRepository;
import com.akiba.transaction.services.AnomalyDetectionService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQMessage;

public class TransactionSaveConsumer {

  private static final String QUEUE_INBOUND     = "transaction.save";
  private static final String QUEUE_SAVINGS     = "savings.contribution";
  private static final String QUEUE_DEAD_LETTER = "dead.letter";

  private final RabbitMQClient rabbitMQ;
  private final TransactionRepository transactionRepo;
  private final AnomalyDetectionService anomalyService;

  public TransactionSaveConsumer(
    RabbitMQClient rabbitMQ,
    TransactionRepository transactionRepo,
    AnomalyDetectionService anomalyService) {
    this.rabbitMQ        = rabbitMQ;
    this.transactionRepo = transactionRepo;
    this.anomalyService  = anomalyService;
  }

  /**
   * Registers the consumer. Called once during MainVerticle startup.
   * basicConsumer returns a Future<RabbitMQConsumer> — we attach our handler to it.
   */
  public Future<Void> start() {
    // queueDeclare first — ensures the queue exists before we try to consume from it.
    // durable=true means the queue survives a RabbitMQ restart.
    return rabbitMQ.queueDeclare(QUEUE_INBOUND, true, false, false)
      .compose(ok -> rabbitMQ.basicConsumer(QUEUE_INBOUND))
      .onSuccess(consumer -> consumer.handler(this::handleMessage))
      .onFailure(err -> System.err.println("[TransactionSaveConsumer] Failed to register: " + err.getMessage()))
      .mapEmpty();
  }

  private void handleMessage(RabbitMQMessage message) {
    String body = message.body().toString();

    processMessage(body)
      // ACK: tell RabbitMQ "I processed this successfully, remove it from the queue"
      // deliveryTag is the unique ID RabbitMQ assigns to each message delivery
      .onSuccess(v -> rabbitMQ.basicAck(message.envelope().getDeliveryTag(), false)
        .onFailure(e -> System.err.println("[TransactionSaveConsumer] ACK failed: " + e.getMessage())))
      // NACK: processing failed — send to dead letter, don't requeue
      .onFailure(err -> {
        rabbitMQ.basicNack(message.envelope().getDeliveryTag(), false, false);
        sendToDeadLetter(body, err.getMessage());
      });
  }

  private Future<Void> processMessage(String rawBody) {
    JsonObject payload = new JsonObject(rawBody);
    String userId      = payload.getString("userId");
    JsonArray txArray  = payload.getJsonArray("transactions");

    return anomalyService.flagAnomalies(userId, txArray)
      .compose(flaggedArray -> transactionRepo.bulkInsert(userId, flaggedArray))
      .compose(v -> routeSavingsTransactions(userId, txArray));
  }

  /**
   * Transactions categorized as "Savings" are forwarded to the savings-service.
   * This is fire-and-forget — a routing failure should not fail the main insert.
   */
  private Future<Void> routeSavingsTransactions(String userId, JsonArray transactions) {
    for (int i = 0; i < transactions.size(); i++) {
      JsonObject tx = transactions.getJsonObject(i);
      if ("Savings".equalsIgnoreCase(tx.getString("category"))) {
        JsonObject msg = new JsonObject()
          .put("userId",        userId)
          .put("amount",        tx.getDouble("amount"))
          .put("transactionId", tx.getString("id"));

        rabbitMQ.basicPublish("", QUEUE_SAVINGS, msg.toBuffer())
          .onFailure(e -> System.err.println("[TransactionSaveConsumer] Savings publish failed: " + e.getMessage()));
      }
    }
    return Future.succeededFuture();
  }

  private void sendToDeadLetter(String originalBody, String reason) {
    JsonObject dead = new JsonObject()
      .put("originalPayload", originalBody)
      .put("error",           reason)
      .put("timestamp",       System.currentTimeMillis())
      .put("source",          "transaction-service");

    rabbitMQ.basicPublish("", QUEUE_DEAD_LETTER, dead.toBuffer())
      .onFailure(e -> System.err.println("[TransactionSaveConsumer] Dead letter publish failed: " + e.getMessage()));
  }
}
