package com.akiba.budget.consumers;

import com.akiba.budget.repositories.BudgetRepository;
import com.akiba.budget.services.BudgetCacheService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQMessage;

import java.time.LocalDateTime;

public class PaymentCompletedConsumer {

    private static final String QUEUE_INBOUND     = "payment.completed";
    private static final String QUEUE_BUDGET_ALERT = "budget.alert";
    private static final String QUEUE_DEAD_LETTER  = "dead.letter";

    // Alert threshold — warn the user when they've spent 90% of their budget
    private static final double ALERT_THRESHOLD = 0.90;

    private final RabbitMQClient        rabbitMQ;
    private final BudgetRepository      budgetRepo;
    private final BudgetCacheService    cacheService;

    public PaymentCompletedConsumer(
            RabbitMQClient rabbitMQ,
            BudgetRepository budgetRepo,
            BudgetCacheService cacheService) {
        this.rabbitMQ     = rabbitMQ;
        this.budgetRepo   = budgetRepo;
        this.cacheService = cacheService;
    }

    public Future<Void> start() {
        return rabbitMQ.queueDeclare(QUEUE_INBOUND, true, false, false)
            .compose(ok -> rabbitMQ.basicConsumer(QUEUE_INBOUND))
            .onSuccess(consumer -> consumer.handler(this::handleMessage))
            .onFailure(err -> System.err.println("[PaymentCompletedConsumer] Failed to register: " + err.getMessage()))
            .mapEmpty();
    }

    private void handleMessage(RabbitMQMessage message) {
        String body = message.body().toString();

        processMessage(body)
            .onSuccess(v -> rabbitMQ.basicAck(message.envelope().getDeliveryTag(), false))
            .onFailure(err -> {
                System.err.println("[PaymentCompletedConsumer] Processing failed: " + err.getMessage());
                rabbitMQ.basicNack(message.envelope().getDeliveryTag(), false, false);
                sendToDeadLetter(body, err.getMessage());
            });
    }

    private Future<Void> processMessage(String rawBody) {
        JsonObject payment = new JsonObject(rawBody);

        String userId   = payment.getString("userId");
        double amount   = payment.getDouble("amount");
        String category = payment.getString("category");

        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        int year  = now.getYear();

        // Step 1: record the spend in Postgres
        return budgetRepo.addSpend(userId, category, amount, month, year)
            // Step 2: invalidate the stale cache entry so next read is fresh
            .compose(v -> cacheService.invalidate(userId, category, month, year))
            // Step 3: fetch updated totals to check alert threshold
            .compose(v -> budgetRepo.getOneCategorySpend(userId, category, month, year))
            // Step 4: publish alert if >= 90% of limit used
            .compose(data -> maybePublishAlert(userId, category, data));
    }

    /**
     * Only fires the alert if the category has a limit set AND spend is at or above 90%.
     * If no limit is set, we can't know what 90% means — so we skip the alert.
     */
    private Future<Void> maybePublishAlert(String userId, String category, JsonObject data) {
        if (data == null) return Future.succeededFuture();

        double limit   = data.getDouble("monthlyLimit");
        double spent   = data.getDouble("amountSpent");

        if (limit <= 0) return Future.succeededFuture();

        double percentage = spent / limit;
        if (percentage < ALERT_THRESHOLD) return Future.succeededFuture();

        JsonObject alert = new JsonObject()
            .put("userId",     userId)
            .put("category",   category)
            .put("spent",      spent)
            .put("limit",      limit)
            .put("percentage", (int) (percentage * 100));

        return rabbitMQ.basicPublish("", QUEUE_BUDGET_ALERT, alert.toBuffer())
            .onFailure(e -> System.err.println("[PaymentCompletedConsumer] Alert publish failed: " + e.getMessage()))
            .mapEmpty();
    }

    private void sendToDeadLetter(String originalBody, String reason) {
        JsonObject dead = new JsonObject()
            .put("originalPayload", originalBody)
            .put("error",           reason)
            .put("timestamp",       System.currentTimeMillis())
            .put("source",          "budget-service");

        rabbitMQ.basicPublish("", QUEUE_DEAD_LETTER, dead.toBuffer())
            .onFailure(e -> System.err.println("[PaymentCompletedConsumer] Dead letter failed: " + e.getMessage()));
    }
}
