package com.akiba.ai.services;

import com.akiba.ai.providers.AiProvider;
import com.akiba.ai.repositories.AiRepository;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * ReportGenerationConsumer subscribes to the "report.generate" RabbitMQ queue
 * and produces monthly financial reports using the AI.
 *
 * Vert.x 5 changes:
 *   - basicPublish() no longer accepts RabbitMQPublishOptions as a parameter.
 *     The v5 signature is: basicPublish(exchange, routingKey, body) → Future<Void>.
 *   - RabbitMQClient.start() is now called in MainVerticle before this consumer
 *     is registered — so we can safely call basicConsumer() here without
 *     worrying about connection state.
 */
public class ReportGenerationConsumer {

  private static final Logger log = LoggerFactory.getLogger(ReportGenerationConsumer.class);

  private static final String QUEUE_IN         = "report.generate";
  private static final String QUEUE_NOTIFY     = "notifications.report_ready";
  private static final String TRANSACTION_BASE = "http://transaction-service:8082";
  private static final String BUDGET_BASE      = "http://budget-service:8086";

  private final RabbitMQClient rabbitMQ;
  private final AiProvider     aiProvider;
  private final AiRepository   repository;
  private final WebClient      webClient;

  public ReportGenerationConsumer(
    RabbitMQClient rabbitMQ,
    AiProvider aiProvider,
    AiRepository repository,
    WebClient webClient
  ) {
    this.rabbitMQ   = rabbitMQ;
    this.aiProvider = aiProvider;
    this.repository = repository;
    this.webClient  = webClient;
  }

  /**
   * Registers the queue consumer.
   * Called from MainVerticle after rabbitMQ.start() has resolved.
   */
  public Future<Void> start() {
    return rabbitMQ.queueDeclare(QUEUE_IN, true, false, false)
      .compose(v -> rabbitMQ.queueDeclare(QUEUE_NOTIFY, true, false, false))
      .compose(v -> rabbitMQ.basicConsumer(QUEUE_IN))
      .onSuccess(consumer -> {
        consumer.handler(this::handleMessage);
        log.info("Listening on RabbitMQ queue: {}", QUEUE_IN);
      })
      .mapEmpty();
  }

  private void handleMessage(RabbitMQMessage message) {
    JsonObject body   = message.body().toJsonObject();
    UUID       userId = UUID.fromString(body.getString("userId"));
    int        month  = body.getInteger("month");
    int        year   = body.getInteger("year");

    log.info("Generating report for user {} — {}/{}", userId, month, year);

    repository.createReport(userId, month, year)
      .compose(report ->
        buildReportData(userId, month, year)
          .compose(data -> generateReportWithAi(data, month, year))
          .compose(content -> repository.updateReportContent(report.id, content, "COMPLETE"))
          .compose(v -> publishCompletion(userId, report.id, month, year))
          .onFailure(err -> {
            log.error("Report generation failed for user {}: {}", userId, err.getMessage());
            // Best-effort — mark failed so the client isn't left polling GENERATING forever.
            repository.updateReportContent(report.id, null, "FAILED");
          })
      );
  }

  private Future<JsonObject> buildReportData(UUID userId, int month, int year) {
    String txUrl     = TRANSACTION_BASE + "/internal/report/" + userId + "?month=" + month + "&year=" + year;
    String budgetUrl = BUDGET_BASE      + "/internal/report/" + userId + "?month=" + month + "&year=" + year;

    Future<JsonObject> txFuture = webClient.getAbs(txUrl).send()
      .map(r -> r.statusCode() == 200 ? r.bodyAsJsonObject() : new JsonObject())
      .recover(e -> Future.succeededFuture(new JsonObject()));

    Future<JsonObject> budgetFuture = webClient.getAbs(budgetUrl).send()
      .map(r -> r.statusCode() == 200 ? r.bodyAsJsonObject() : new JsonObject())
      .recover(e -> Future.succeededFuture(new JsonObject()));

    return Future.all(txFuture, budgetFuture)
      .map(cf -> new JsonObject()
        .put("transactions", (JsonObject) cf.resultAt(0))
        .put("budget",       (JsonObject) cf.resultAt(1)));
  }

  private Future<String> generateReportWithAi(JsonObject data, int month, int year) {
    String systemPrompt =
      "You are a financial analyst. Generate a clear, friendly monthly financial " +
        "report in Markdown format. Include: Executive Summary, Income vs Expenses, " +
        "Top Spending Categories, Budget Performance, Savings Progress, and 3 " +
        "actionable recommendations. Use Ksh for all amounts.";

    String userPrompt = "Generate my financial report for month " + month + "/" + year +
      " using this data:\n" + data.encodePrettily();

    return aiProvider.complete(systemPrompt, userPrompt, List.of());
  }

  /**
   * Vert.x 5 basicPublish signature:
   *   basicPublish(exchange, routingKey, body) → Future<Void>
   *
   * The RabbitMQPublishOptions overload was removed in v5. Pass an empty
   * string for exchange to use the default direct exchange.
   */
  private Future<Void> publishCompletion(UUID userId, UUID reportId, int month, int year) {
    JsonObject notification = new JsonObject()
      .put("type",     "REPORT_READY")
      .put("userId",   userId.toString())
      .put("reportId", reportId.toString())
      .put("month",    month)
      .put("year",     year);

    return rabbitMQ.basicPublish("", QUEUE_NOTIFY, Buffer.buffer(notification.encode()));
  }
}
