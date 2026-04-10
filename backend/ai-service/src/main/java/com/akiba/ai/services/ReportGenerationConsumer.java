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
 * ReportGenerationConsumer subscribes to the "report.generate" RabbitMQ
 * queue and produces monthly financial reports using the AI.
 *
 * Why RabbitMQ? Report generation is slow (~5-10 seconds). If we did
 * it synchronously in an HTTP request, the user would be staring at a
 * spinner. Instead: the user requests a report → we enqueue a job →
 * return 202 Accepted immediately → this consumer runs the AI call in
 * the background → publishes completion to the notifications queue.
 *
 * NOTE (Vert.x 5): RabbitMQPublishOptions was removed. basicPublish now
 * accepts the exchange, routing key, and Buffer directly — no options
 * object needed for the default case.
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
   * Registers the queue consumer. Call this once from the main verticle's start().
   */
  public Future<Void> start() {
    return rabbitMQ.basicConsumer(QUEUE_IN).compose(consumer -> {
      consumer.handler(this::handleMessage);
      log.info("Listening on RabbitMQ queue: {}", QUEUE_IN);
      return Future.succeededFuture();
    });
  }

  private void handleMessage(RabbitMQMessage message) {
    JsonObject body   = message.body().toJsonObject();
    UUID       userId = UUID.fromString(body.getString("userId"));
    int        month  = body.getInteger("month");
    int        year   = body.getInteger("year");

    log.info("Generating report for user {} → {}/{}", userId, month, year);

    repository.createReport(userId, month, year)
      .compose(report ->
        buildReportData(userId, month, year)
          .compose(data -> generateReportWithAi(data, month, year))
          .compose(content ->
            repository.updateReportContent(report.id, content, "COMPLETE")
              .compose(v -> publishCompletion(userId, report.id, month, year))
          )
          .onFailure(err -> {
            log.error("Report generation failed for user {}: {}", userId, err.getMessage());
            repository.updateReportContent(report.id, null, "FAILED");
          })
      );
  }

  /**
   * Fetches transaction + budget data for the given month/year from
   * their respective services. Uses internal HTTP (no auth needed
   * between services on the same Docker network).
   */
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
        .put("budget",       (JsonObject) cf.resultAt(1))
      );
  }

  private Future<String> generateReportWithAi(JsonObject data, int month, int year) {
    String systemPrompt =
      "You are a financial analyst. Generate a clear, friendly monthly financial report " +
        "in Markdown format. Include: Executive Summary, Income vs Expenses, Top Spending Categories, " +
        "Budget Performance, Savings Progress, and 3 actionable recommendations. Use Ksh for amounts.";

    String userPrompt =
      "Generate my financial report for month " + month + "/" + year +
        " using this data:\n" + data.encodePrettily();

    return aiProvider.complete(systemPrompt, userPrompt, List.of());
  }

  /**
   * Publishes a notification so the frontend (via WebSocket / SSE) knows
   * the report is ready.
   *
   * Vert.x 5 API: basicPublish(exchange, routingKey, body) — the old
   * RabbitMQPublishOptions overload no longer exists.
   */
  private Future<Void> publishCompletion(UUID userId, UUID reportId, int month, int year) {
    JsonObject notification = new JsonObject()
      .put("type",     "REPORT_READY")
      .put("userId",   userId.toString())
      .put("reportId", reportId.toString())
      .put("month",    month)
      .put("year",     year);

    return rabbitMQ.basicPublish(
      "",             // default exchange
      QUEUE_NOTIFY,   // routing key
      Buffer.buffer(notification.encode())
    ).mapEmpty();
  }
}
