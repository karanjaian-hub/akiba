package com.akiba.transaction.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class AnomalyDetectionService {

  private final Pool pool;

  public AnomalyDetectionService(Pool pool) {
    this.pool = pool;
  }

  /**
   * Flags a transaction as anomalous if its amount > 3x the user's
   * average spend in that category over the last 30 days.
   *
   * We fetch ALL category averages in a single query, then apply
   * the rule in memory — avoids N database calls for N transactions.
   */
  public Future<JsonArray> flagAnomalies(String userId, JsonArray transactions) {
    return fetchCategoryAverages(userId)
      .map(averages -> applyAnomalyFlags(transactions, averages));
  }

  private Future<JsonObject> fetchCategoryAverages(String userId) {
    String sql = """
                SELECT category, AVG(amount) AS avg_amount
                FROM transactions.records
                WHERE user_id = $1
                  AND date >= NOW() - INTERVAL '30 days'
                  AND type = 'DEBIT'
                GROUP BY category
                """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .map(rows -> {
        JsonObject averages = new JsonObject();
        for (Row row : rows) {
          String category  = row.getString("category");
          Double avgAmount = row.getDouble("avg_amount");
          if (category != null && avgAmount != null) {
            averages.put(category, avgAmount);
          }
        }
        return averages;
      })
      .onFailure(err -> System.err.println("[AnomalyDetectionService] Failed to fetch averages: " + err.getMessage()));
  }

  private JsonArray applyAnomalyFlags(JsonArray transactions, JsonObject categoryAverages) {
    JsonArray result = new JsonArray();
    for (int i = 0; i < transactions.size(); i++) {
      JsonObject tx   = transactions.getJsonObject(i).copy();
      String category = tx.getString("category");
      Double amount   = tx.getDouble("amount");

      if (amount == null) {
        tx.put("anomalous", false);
        result.add(tx);
        continue;
      }

      Double avg = categoryAverages.getDouble(category);
      // If no historical average exists (new user, new category), we can't flag anything —
      // better to have a false negative than a false positive on day one.
      boolean isAnomalous = avg != null && amount > (avg * 3.0);
      tx.put("anomalous", isAnomalous);
      result.add(tx);
    }
    return result;
  }
}
