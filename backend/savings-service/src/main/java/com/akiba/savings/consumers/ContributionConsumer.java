package com.akiba.savings.consumers;

import com.akiba.savings.models.SavingsGoal;
import com.akiba.savings.repositories.SavingsRepository;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContributionConsumer extends VerticleBase {

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
  public Future<?> start() {
    return rabbitMQ.queueDeclare(CONTRIBUTION_QUEUE, true, false, false)
      .compose(v -> rabbitMQ.queueDeclare(ALERTS_QUEUE, true, false, false))
      .compose(v -> rabbitMQ.basicConsumer(CONTRIBUTION_QUEUE))
      .onSuccess(consumer -> {
        consumer.handler(message -> {
          JsonObject payload = new JsonObject(message.body().toString());
          handleContribution(payload)
            .onFailure(err -> log.error("Error processing contribution: {}", payload.encode(), err));
        });
        log.info("savings.contribution consumer active");
      })
      .onFailure(err -> log.error("Failed to start savings.contribution consumer", err))
      .mapEmpty();
  }

  private Future<Void> handleContribution(JsonObject payload) {
    String userId        = payload.getString("userId");
    double amount        = payload.getDouble("amount", 0.0);
    String transactionId = payload.getString("transactionId");

    if (userId == null || amount <= 0) {
      log.warn("Skipping malformed contribution payload: {}", payload.encode());
      return Future.succeededFuture();
    }

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

        return repository.addContribution(goalId, userId, amount, transactionId, "Auto-credited")
          .compose(v -> refreshCache(userId, goalId, goalJson, amount))
          .compose(v -> publishAlertsIfNeeded(userId, goalId, goalName, current + amount, target, goalJson));
      });
  }

  private Future<Void> refreshCache(String userId, String goalId, JsonObject goalJson, double addedAmount) {
    JsonObject updated = goalJson.copy().put("currentAmount",
      String.valueOf(Double.parseDouble(goalJson.getString("currentAmount", "0")) + addedAmount));
    return redis.setex(CACHE_KEY_PREFIX + userId + ":" + goalId,
      String.valueOf(CACHE_TTL_SECONDS), updated.encode()).mapEmpty();
  }

  private Future<Void> publishAlertsIfNeeded(String userId, String goalId, String goalName,
                                             double newTotal, double target, JsonObject goalJson) {
    if (newTotal >= target) {
      return publishAlert(new JsonObject()
        .put("type", "GOAL_COMPLETED")
        .put("userId", userId)
        .put("goalId", goalId)
        .put("goalName", goalName));
    }

    SavingsGoal goal = SavingsGoal.fromJson(goalJson.copy().put("currentAmount", String.valueOf(newTotal)));
    if (!goal.isOnTrack && goal.daysRemaining > 0) {
      return publishAlert(new JsonObject()
        .put("type", "BEHIND_PACE")
        .put("userId", userId)
        .put("goalId", goalId)
        .put("goalName", goalName)
        .put("requiredWeekly", goal.requiredWeeklySaving));
    }

    return Future.succeededFuture();
  }

  private Future<Void> publishAlert(JsonObject alert) {
    // Vert.x 5: basicPublish(exchange, routingKey, BasicProperties, Buffer)
    return rabbitMQ.basicPublish("", ALERTS_QUEUE, null, Buffer.buffer(alert.encode()))
      .onFailure(err -> log.warn("Failed to publish savings alert: {}", err.getMessage()))
      .mapEmpty();
  }
}
