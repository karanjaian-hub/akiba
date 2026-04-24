package com.akiba.savings.handlers;

import com.akiba.savings.models.SavingsGoal;
import com.akiba.savings.repositories.SavingsRepository;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SavingsHandler {

  private static final Logger log = LoggerFactory.getLogger(SavingsHandler.class);

  private static final String CACHE_KEY_PREFIX  = "savings:";
  private static final int    CACHE_TTL_SECONDS = 300;
  private static final String ALERTS_QUEUE      = "savings.alert";

  private final SavingsRepository repository;
  private final RedisAPI          redis;
  private final RabbitMQClient    rabbitMQ;

  public SavingsHandler(SavingsRepository repository, RedisAPI redis, RabbitMQClient rabbitMQ) {
    this.repository = repository;
    this.redis      = redis;
    this.rabbitMQ   = rabbitMQ;
  }

  // ── GET /savings/goals ────────────────────────────────────────────────────

  public void getGoals(RoutingContext ctx) {
    String userId = ctx.user().subject();
    repository.getActiveGoals(userId)
      .compose(goalRows -> enrichGoalsFromCache(userId, goalRows))
      .onSuccess(goals -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("goals", goals).encode()))
      .onFailure(err -> {
        log.error("Failed to fetch goals for user {}", userId, err);
        replyError(ctx, 500, "Could not retrieve savings goals");
      });
  }

  private Future<JsonArray> enrichGoalsFromCache(String userId, JsonArray goalRows) {
    List<Future<JsonObject>> futures = new ArrayList<>();
    for (Object obj : goalRows) {
      JsonObject row  = (JsonObject) obj;
      String goalId   = row.getString("id");
      String cacheKey = CACHE_KEY_PREFIX + userId + ":" + goalId;

      futures.add(redis.get(cacheKey)
        .map(cached -> SavingsGoal.fromJson(cached != null
          ? new JsonObject(cached.toString()) : row).toJson())
        .recover(err -> Future.succeededFuture(SavingsGoal.fromJson(row).toJson())));
    }
    return Future.all(futures).map(results -> {
      JsonArray enriched = new JsonArray();
      results.list().forEach(r -> enriched.add((JsonObject) r));
      return enriched;
    });
  }

  // ── POST /savings/goals ───────────────────────────────────────────────────

  public void createGoal(RoutingContext ctx) {
    JsonObject body   = ctx.body().asJsonObject();
    String     userId = ctx.user().subject();

    if (body == null || body.getString("name") == null
      || body.getDouble("targetAmount") == null
      || body.getString("deadline") == null) {
      replyError(ctx, 400, "name, targetAmount, and deadline are required");
      return;
    }

    repository.createGoal(userId, body)
      .onSuccess(goalId -> ctx.response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("goalId", goalId).encode()))
      .onFailure(err -> {
        log.error("Failed to create goal for user {}", userId, err);
        replyError(ctx, 500, "Could not create savings goal");
      });
  }

  // ── PUT /savings/goals/:id ────────────────────────────────────────────────

  public void updateGoal(RoutingContext ctx) {
    String goalId  = ctx.pathParam("id");
    String userId  = ctx.user().subject();
    JsonObject updates = ctx.body().asJsonObject();

    if (updates == null) { replyError(ctx, 400, "Request body required"); return; }

    repository.updateGoal(goalId, userId, updates)
      .compose(v -> invalidateCache(userId, goalId))
      .onSuccess(v -> ctx.response().setStatusCode(204).end())
      .onFailure(err -> {
        log.error("Failed to update goal {}", goalId, err);
        replyError(ctx, 500, "Could not update goal");
      });
  }

  // ── DELETE /savings/goals/:id ─────────────────────────────────────────────

  public void archiveGoal(RoutingContext ctx) {
    String goalId = ctx.pathParam("id");
    String userId = ctx.user().subject();

    repository.archiveGoal(goalId, userId)
      .compose(v -> invalidateCache(userId, goalId))
      .onSuccess(v -> ctx.response().setStatusCode(204).end())
      .onFailure(err -> {
        log.error("Failed to archive goal {}", goalId, err);
        replyError(ctx, 500, "Could not archive goal");
      });
  }

  // ── POST /savings/goals/:id/contribute ───────────────────────────────────

  public void addManualContribution(RoutingContext ctx) {
    String goalId   = ctx.pathParam("id");
    String userId   = ctx.user().subject();
    JsonObject body = ctx.body().asJsonObject();

    if (body == null || body.getDouble("amount") == null) {
      replyError(ctx, 400, "amount is required");
      return;
    }

    double amount = body.getDouble("amount");
    String note   = body.getString("note");

    repository.addContribution(goalId, userId, amount, null, note)
      .compose(v -> repository.getGoalById(goalId, userId))
      .compose(goalJson -> {
        if (goalJson == null) return Future.succeededFuture(null);
        SavingsGoal goal = SavingsGoal.fromJson(goalJson);
        if (goal.currentAmount >= goal.targetAmount) {
          publishAlert(new JsonObject()
            .put("type", "GOAL_COMPLETED")
            .put("userId", userId)
            .put("goalId", goalId)
            .put("goalName", goal.name));
        }
        return invalidateCache(userId, goalId).map(goal.toJson());
      })
      .onSuccess(goal -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(goal != null ? goal.encode() : "{}"))
      .onFailure(err -> {
        log.error("Failed to add contribution to goal {}", goalId, err);
        replyError(ctx, 500, "Could not add contribution");
      });
  }

  // ── GET /savings/goals/:id/history ────────────────────────────────────────

  public void getContributionHistory(RoutingContext ctx) {
    String goalId = ctx.pathParam("id");
    String userId = ctx.user().subject();

    repository.getContributionHistory(goalId, userId)
      .onSuccess(history -> ctx.response()
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("contributions", history).encode()))
      .onFailure(err -> {
        log.error("Failed to fetch contribution history for goal {}", goalId, err);
        replyError(ctx, 500, "Could not retrieve contribution history");
      });
  }

  // ── GET /health ───────────────────────────────────────────────────────────

  public void healthCheck(RoutingContext ctx) {
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("status", "UP").put("service", "savings-service").encode());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Future<Void> invalidateCache(String userId, String goalId) {
    return redis.del(List.of(CACHE_KEY_PREFIX + userId + ":" + goalId)).mapEmpty();
  }

  private void publishAlert(JsonObject alert) {
    // Vert.x 5: basicPublish(exchange, routingKey, BasicProperties, Buffer)
    rabbitMQ.basicPublish("", ALERTS_QUEUE, null, Buffer.buffer(alert.encode()))
      .onFailure(err -> log.warn("Failed to publish savings alert: {}", err.getMessage()));
  }

  private void replyError(RoutingContext ctx, int status, String message) {
    ctx.response()
      .setStatusCode(status)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
