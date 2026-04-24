package com.akiba.savings.repositories;

import com.akiba.savings.models.SavingsGoal;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SavingsRepository {

  private static final Logger log = LoggerFactory.getLogger(SavingsRepository.class);

  private final Pool pool;

  public SavingsRepository(Pool pool) {
    this.pool = pool;
  }

  // ── Goals ──────────────────────────────────────────────────────────────────

  public Future<JsonArray> getActiveGoals(String userId) {
    String sql = """
      SELECT id, user_id, name, target_amount, current_amount,
             deadline::text, icon, status, created_at
        FROM savings.goals
       WHERE user_id = $1 AND status = 'ACTIVE'
       ORDER BY created_at ASC
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .map(this::rowsToJsonArray);
  }

  public Future<JsonObject> getLowestProgressGoal(String userId) {
    String sql = """
      SELECT id, user_id, name, target_amount, current_amount,
             deadline::text, icon, status, created_at
        FROM savings.goals
       WHERE user_id = $1 AND status = 'ACTIVE'
             AND current_amount < target_amount
       ORDER BY (current_amount / NULLIF(target_amount, 0)) ASC
       LIMIT 1
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .map(rows -> rows.iterator().hasNext() ? rowToJson(rows.iterator().next()) : null);
  }

  public Future<String> createGoal(String userId, JsonObject body) {
    String sql = """
      INSERT INTO savings.goals (user_id, name, target_amount, deadline, icon)
      VALUES ($1, $2, $3, $4::date, $5)
      RETURNING id
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(
        UUID.fromString(userId),
        body.getString("name"),
        body.getDouble("targetAmount"),
        body.getString("deadline"),
        body.getString("icon", "🎯")))
      .map(rows -> rows.iterator().next().getString("id"));
  }

  public Future<Void> updateGoal(String goalId, String userId, JsonObject updates) {
    String sql = """
      UPDATE savings.goals
         SET name          = COALESCE($1, name),
             target_amount = COALESCE($2, target_amount),
             deadline      = COALESCE($3::date, deadline),
             icon          = COALESCE($4, icon)
       WHERE id = $5 AND user_id = $6
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(
        updates.getString("name"),
        updates.getDouble("targetAmount"),
        updates.getString("deadline"),
        updates.getString("icon"),
        UUID.fromString(goalId),
        UUID.fromString(userId)))
      .mapEmpty();
  }

  public Future<Void> archiveGoal(String goalId, String userId) {
    String sql = "UPDATE savings.goals SET status = 'ARCHIVED' WHERE id = $1 AND user_id = $2";
    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(goalId), UUID.fromString(userId)))
      .mapEmpty();
  }

  // ── Contributions ─────────────────────────────────────────────────────────

  public Future<Void> addContribution(String goalId, String userId, double amount,
                                      String transactionId, String note) {
    String insertSql = """
      INSERT INTO savings.contributions (goal_id, user_id, amount, transaction_id, note)
      VALUES ($1, $2, $3, $4, $5)
      """;
    String updateSql = """
      UPDATE savings.goals SET current_amount = current_amount + $1
       WHERE id = $2 AND user_id = $3
      """;
    UUID goalUuid = UUID.fromString(goalId);
    UUID userUuid = UUID.fromString(userId);
    return pool.preparedQuery(insertSql)
      .execute(Tuple.of(goalUuid, userUuid, amount, transactionId, note))
      .compose(v -> pool.preparedQuery(updateSql).execute(Tuple.of(amount, goalUuid, userUuid)))
      .mapEmpty();
  }

  public Future<JsonObject> getGoalById(String goalId, String userId) {
    String sql = """
      SELECT id, user_id, name, target_amount, current_amount,
             deadline::text, icon, status, created_at
        FROM savings.goals
       WHERE id = $1 AND user_id = $2
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(goalId), UUID.fromString(userId)))
      .map(rows -> rows.iterator().hasNext() ? rowToJson(rows.iterator().next()) : null);
  }

  public Future<JsonArray> getContributionHistory(String goalId, String userId) {
    String sql = """
      SELECT c.id, c.amount, c.note, c.transaction_id, c.created_at
        FROM savings.contributions c
        JOIN savings.goals g ON g.id = c.goal_id
       WHERE c.goal_id = $1 AND g.user_id = $2
       ORDER BY c.created_at DESC
      """;
    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(goalId), UUID.fromString(userId)))
      .map(this::rowsToJsonArray);
  }

  // ── Row mapping ───────────────────────────────────────────────────────────

  private JsonArray rowsToJsonArray(RowSet<Row> rows) {
    JsonArray result = new JsonArray();
    rows.forEach(row -> result.add(rowToJson(row)));
    return result;
  }

  private JsonObject rowToJson(Row row) {
    JsonObject json = new JsonObject();
    for (int i = 0; i < row.size(); i++) {
      Object value = row.getValue(i);
      json.put(toCamelCase(row.getColumnName(i)), value != null ? value.toString() : null);
    }
    return json;
  }

  private String toCamelCase(String snake) {
    StringBuilder result = new StringBuilder();
    boolean nextUpper = false;
    for (char c : snake.toCharArray()) {
      if (c == '_') { nextUpper = true; }
      else { result.append(nextUpper ? Character.toUpperCase(c) : c); nextUpper = false; }
    }
    return result.toString();
  }
}
