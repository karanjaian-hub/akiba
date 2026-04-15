package com.akiba.savings.consumers;

import com.akiba.savings.models.SavingsGoal;
import com.akiba.savings.repositories.SavingsRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Listens on the 'savings.contribution' RabbitMQ queue.
 *
 * When a transaction is categorised as 'Savings' (by the parsing-service or
 * AI-service), it publishes a message here. We find the user's lowest-progress
 * active goal and credit the contribution to it automatically.
 *
 * This runs as its own Verticle so it has a clean lifecycle separate from HTTP.
 */
public class ContributionConsumer extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(ContributionConsumer.class);

  private static final String CONTRIBUTION_QUEUE = "savings.contribution";
  private static final String ALERTS_QUEUE       = "savings.alert";
  private static final String CACHE_KEY_PREFIX   = "savings:";
  private static final int    CACHE_TTL_SECONDS  = 300;

  private final RabbitMQClient    rabbitMQ;
  private final SavingsRepository repository;
  private final RedisAPI          redis;

  public ContributionConsumer(RabbitMQClient rabbitMQ, SavingsRepository repository, RedisAPI redis) {
    this.rabbitMQ   = rabbitMQ;
    this.repository = repository;
    this.redis      = redis;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    rabbitMQ.basicConsumer(CONTRIBUTION_QUEUE, result -> {
      if (result.failed()) {
        log.error("Failed to start savings.contribution consumer", result.cause());
        startPromise.fail(result.cause());
        return;
      }

      RabbitMQConsumer consumer = result.result();
      consumer.handler(message -> {
        JsonObject payload = new JsonObject(message.body().toString());
        handleContribution(payload)
          .onFailure(err -> log.error("Error processing contribution: {}", payload.encode(), err));
      });

      log.info("savings.contribution consumer active");
      startPromise.complete();
    });
  }

  // ─────────────────────────────────────────────────────────────────────────

  private Future<Void> handleContribution(JsonObject payload) {
    String userId        = payload.getString("userId");
    double amount        = payload.getDouble("amount", 0.0);
    String transactionId = payload.getString("transactionId");

    if (userId == null || amount <= 0) {
      log.warn("Skipping malformed contribution payload: {}", payload.encode());
      return Future.succeededFuture();
    }

    // Find the goal that needs the most help, then credit it
    return repository.getLowestProgressGoal(userId)
      .compose(goalJson -> {
        if (goalJson == null) {
          log.info("No active goals for user {} — contribution ignored", userId);
          return Future.succeededFuture();
        }

        String goalId   = goalJson.getString("id");
        String goalName = goalJson.getString("name");
        double current  = Double.parseDouble(goalJson.getString("currentAmount", "0"));
        double target   = Double.parseDouble(goalJson.getString("targetAmount", "0"));

        return repository.addContribution(goalId, userId, amount, transactionId, "Auto-credited from transaction")
          .compose(v -> refreshCache(userId, goalId, goalJson, amount))
          .compose(v -> publishAlertsIfNeeded(userId, goalId, goalName, current + amount, target, goalJson));
      });
  }

  private Future<Void> refreshCache(String userId, String goalId, JsonObject goalJson, double addedAmount) {
    // Update the cached version with the new amount so GET /savings/goals stays fresh
    JsonObject updated = goalJson.copy()
      .put("currentAmount", String.valueOf(
        Double.parseDouble(goalJson.getString("currentAmount", "0")) + addedAmount));

    String cacheKey = CACHE_KEY_PREFIX + userId + ":" + goalId;
    return redis.setex(cacheKey, String.valueOf(CACHE_TTL_SECONDS), updated.encode())
      .mapEmpty();
  }

  private Future<Void> publishAlertsIfNeeded(
      String userId, String goalId, String goalName,
      double newTotal, double target, JsonObject goalJson) {

    // Alert 1: Goal just completed
    if (newTotal >= target) {
      return publishAlert(new JsonObject()
        .put("type",     "GOAL_COMPLETED")
        .put("userId",   userId)
        .put("goalId",   goalId)
        .put("goalName", goalName));
    }

    // Alert 2: Behind pace — work out how much extra per week they need
    SavingsGoal goal = SavingsGoal.fromJson(goalJson
      .put("currentAmount", String.valueOf(newTotal)));

    if (!goal.isOnTrack && goal.daysRemaining > 0) {
      return publishAlert(new JsonObject()
        .put("type",           "BEHIND_PACE")
        .put("userId",         userId)
        .put("goalId",         goalId)
        .put("goalName",       goalName)
        .put("requiredWeekly", goal.requiredWeeklySaving));
    }

    return Future.succeededFuture();
  }

  private Future<Void> publishAlert(JsonObject alert) {
    log.info("Publishing savings alert: {}", alert.encode());
    return rabbitMQ.basicPublish("", ALERTS_QUEUE,
        new io.vertx.rabbitmq.RabbitMQMessage() {
          @Override public io.vertx.core.buffer.Buffer body() {
            return io.vertx.core.buffer.Buffer.buffer(alert.encode());
          }
          @Override public com.rabbitmq.client.Envelope envelope() { return null; }
          @Override public com.rabbitmq.client.AMQP.BasicProperties properties() { return null; }
          @Override public String consumerTag() { return null; }
        })
      .mapEmpty();
  }
}
