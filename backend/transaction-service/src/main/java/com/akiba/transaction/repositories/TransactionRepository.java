package com.akiba.transaction.repositories;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionRepository {

  private final Pool pool;

  public TransactionRepository(Pool pool) {
    this.pool = pool;
  }

  public Future<Void> bulkInsert(String userId, JsonArray transactions) {
    String sql = """
                INSERT INTO transactions.records
                    (user_id, date, amount, type, merchant, category, source, reference, raw_text, anomalous)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                """;

    List<Tuple> batch = new ArrayList<>();
    for (int i = 0; i < transactions.size(); i++) {
      JsonObject tx = transactions.getJsonObject(i);

      // Accept both "date" (RabbitMQ) and "transaction_date" (Postman)
      String dateStr = tx.getString("date") != null
        ? tx.getString("date")
        : tx.getString("transaction_date");

      // Accept both "rawText" (RabbitMQ) and "description" (Postman)
      String rawText = tx.getString("rawText") != null
        ? tx.getString("rawText")
        : tx.getString("description");

      // Vert.x PG client requires LocalDateTime — it cannot coerce a raw String.
      // OffsetDateTime handles the trailing "Z" (UTC) that Postman sends.
      // LocalDateTime.parse handles plain timestamps with no timezone.
      LocalDateTime parsedDate;
      try {
        if (dateStr != null && (dateStr.endsWith("Z") || dateStr.contains("+"))) {
          parsedDate = OffsetDateTime.parse(dateStr).toLocalDateTime();
        } else if (dateStr != null) {
          parsedDate = LocalDateTime.parse(dateStr);
        } else {
          return Future.failedFuture("date or transaction_date is required");
        }
      } catch (Exception e) {
        return Future.failedFuture("Invalid date format: " + dateStr);
      }

      batch.add(Tuple.of(
        UUID.fromString(userId),
        parsedDate,
        tx.getDouble("amount"),
        tx.getString("type"),
        tx.getString("merchant"),
        tx.getString("category"),
        tx.getString("source"),
        tx.getString("reference"),
        rawText,
        tx.getBoolean("anomalous", false)
      ));
    }

    return pool.preparedQuery(sql)
      .executeBatch(batch)
      .onFailure(err -> System.err.println("[TransactionRepository] bulkInsert failed: " + err.getMessage()))
      .mapEmpty();
  }

  public Future<JsonArray> findAll(
    String userId, int page, int size,
    String category, String dateFrom, String dateTo,
    String type, String source) {

    if (userId == null || userId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("userId must not be null or blank"));
    }

    String sql = """
                SELECT id, date, amount, type, merchant, category, source, reference, anomalous, created_at
                FROM transactions.records
                WHERE user_id = $1
                  AND ($2::varchar IS NULL OR category = $2)
                  AND ($3::timestamp IS NULL OR date >= $3::timestamp)
                  AND ($4::timestamp IS NULL OR date <= $4::timestamp)
                  AND ($5::varchar IS NULL OR type = $5)
                  AND ($6::varchar IS NULL OR source = $6)
                ORDER BY date DESC
                LIMIT $7 OFFSET $8
                """;

    Tuple params = Tuple.of(
      UUID.fromString(userId),
      category, dateFrom, dateTo, type, source,
      size, page * size
    );

    return pool.preparedQuery(sql)
      .execute(params)
      .map(rows -> {
        JsonArray result = new JsonArray();
        for (Row row : rows) {
          result.add(rowToJson(row));
        }
        return result;
      });
  }

  public Future<JsonObject> findById(String userId, String transactionId) {
    if (userId == null || userId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("userId must not be null or blank"));
    }
    if (transactionId == null || transactionId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("transactionId must not be null or blank"));
    }

    String sql = """
                SELECT id, date, amount, type, merchant, category, source, reference, raw_text, anomalous, created_at
                FROM transactions.records
                WHERE id = $1 AND user_id = $2
                """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(transactionId), UUID.fromString(userId)))
      .map(rows -> {
        if (!rows.iterator().hasNext()) return null;
        return rowToJson(rows.iterator().next());
      });
  }

  public Future<Void> updateCategory(String userId, String transactionId, String newCategory) {
    if (userId == null || userId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("userId must not be null or blank"));
    }
    if (transactionId == null || transactionId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("transactionId must not be null or blank"));
    }
    if (newCategory == null || newCategory.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("newCategory must not be null or blank"));
    }

    String sql = """
                UPDATE transactions.records
                SET category = $1
                WHERE id = $2 AND user_id = $3
                """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(newCategory, UUID.fromString(transactionId), UUID.fromString(userId)))
      .mapEmpty();
  }

  public Future<JsonObject> getSummary(String userId) {
    if (userId == null || userId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("userId must not be null or blank"));
    }

    String sql = """
                SELECT
                    SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) AS total_income,
                    SUM(CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END) AS total_expenses
                FROM transactions.records
                WHERE user_id = $1
                  AND DATE_TRUNC('month', date) = DATE_TRUNC('month', NOW())
                """;

    String topCatSql = """
                SELECT category, SUM(amount) AS total
                FROM transactions.records
                WHERE user_id = $1
                  AND type = 'DEBIT'
                  AND DATE_TRUNC('month', date) = DATE_TRUNC('month', NOW())
                GROUP BY category
                ORDER BY total DESC
                LIMIT 5
                """;

    Tuple userTuple = Tuple.of(UUID.fromString(userId));

    return pool.preparedQuery(sql).execute(userTuple)
      .compose(totalsRows -> {
        Row totals = totalsRows.iterator().next();
        JsonObject summary = new JsonObject()
          .put("totalIncome",   totals.getDouble("total_income"))
          .put("totalExpenses", totals.getDouble("total_expenses"));

        return pool.preparedQuery(topCatSql).execute(userTuple)
          .map(catRows -> {
            JsonArray topCategories = new JsonArray();
            for (Row row : catRows) {
              topCategories.add(new JsonObject()
                .put("category", row.getString("category"))
                .put("total",    row.getDouble("total")));
            }
            summary.put("topCategories", topCategories);
            return summary;
          });
      });
  }

  public Future<JsonArray> getTopMerchants(String userId) {
    if (userId == null || userId.isBlank()) {
      return Future.failedFuture(new IllegalArgumentException("userId must not be null or blank"));
    }

    String sql = """
                SELECT merchant, SUM(amount) AS total, COUNT(*) AS tx_count
                FROM transactions.records
                WHERE user_id = $1
                  AND type = 'DEBIT'
                  AND DATE_TRUNC('month', date) = DATE_TRUNC('month', NOW())
                GROUP BY merchant
                ORDER BY total DESC
                LIMIT 5
                """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .map(rows -> {
        JsonArray result = new JsonArray();
        for (Row row : rows) {
          result.add(new JsonObject()
            .put("merchant", row.getString("merchant"))
            .put("total",    row.getDouble("total"))
            .put("txCount",  row.getInteger("tx_count")));
        }
        return result;
      });
  }

  private JsonObject rowToJson(Row row) {
    return new JsonObject()
      .put("id",        row.getUUID("id").toString())
      .put("date",      row.getLocalDateTime("date").toString())
      .put("amount",    row.getDouble("amount"))
      .put("type",      row.getString("type"))
      .put("merchant",  row.getString("merchant"))
      .put("category",  row.getString("category"))
      .put("source",    row.getString("source"))
      .put("reference", row.getString("reference"))
      .put("anomalous", row.getBoolean("anomalous"))
      .put("createdAt", row.getLocalDateTime("created_at").toString());
  }
}
