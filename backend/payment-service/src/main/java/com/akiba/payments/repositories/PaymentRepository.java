package com.akiba.payments.repositories;

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

/**
 * All SQL lives here. Handlers never write raw queries — they call named methods.
 * This makes it easy to test DB logic independently of HTTP logic.
 */
public class PaymentRepository {

  private static final Logger log = LoggerFactory.getLogger(PaymentRepository.class);

  // Vert.x 5: Pool from vertx-sql-client replaces PgPool
  private final Pool pool;

  public PaymentRepository(Pool pool) {
    this.pool = pool;
  }

  // -------------------------------------------------------------------------
  // payments.records
  // -------------------------------------------------------------------------

  public Future<String> createPayment(JsonObject payment) {
    String sql = """
      INSERT INTO payments.records
        (user_id, phone, amount, recipient_type, recipient_id, category,
         description, account_ref, checkout_id)
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
      RETURNING id
      """;

    Tuple params = Tuple.of(
      UUID.fromString(payment.getString("userId")),
      payment.getString("phone"),
      payment.getDouble("amount"),
      payment.getString("recipientType"),
      payment.getString("recipientIdentifier"),
      payment.getString("category"),
      payment.getString("description"),
      payment.getString("accountReference"),
      payment.getString("checkoutId")
    );

    return pool.preparedQuery(sql)
      .execute(params)
      .map(rows -> rows.iterator().next().getString("id"));
  }

  public Future<Void> updatePaymentStatus(String checkoutId, String status, String mpesaReceipt) {
    String sql = """
      UPDATE payments.records
         SET status = $1, mpesa_receipt = $2, updated_at = NOW()
       WHERE checkout_id = $3
      """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(status, mpesaReceipt, checkoutId))
      .mapEmpty();
  }

  public Future<JsonArray> getPaymentHistory(String userId, int page, int size) {
    String sql = """
      SELECT id, amount, recipient_type, recipient_id, category,
             description, status, mpesa_receipt, created_at
        FROM payments.records
       WHERE user_id = $1
       ORDER BY created_at DESC
       LIMIT $2 OFFSET $3
      """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId), size, page * size))
      .map(this::rowsToJsonArray);
  }

  public Future<JsonObject> getPaymentStatus(String paymentId, String userId) {
    String sql = """
      SELECT id, status, mpesa_receipt, amount, recipient_id, created_at
        FROM payments.records
       WHERE id = $1 AND user_id = $2
      """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(paymentId), UUID.fromString(userId)))
      .map(rows -> {
        // Vert.x 5: RowSet implements Iterable — use iterator() via the standard interface
        if (!rows.iterator().hasNext()) return null;
        return rowToJson(rows.iterator().next());
      });
  }

  // -------------------------------------------------------------------------
  // payments.recipients
  // -------------------------------------------------------------------------

  public Future<Void> upsertRecipient(JsonObject recipient) {
    String sql = """
      INSERT INTO payments.recipients
        (user_id, identifier, recipient_type, nickname, default_category)
      VALUES ($1,$2,$3,$4,$5)
      ON CONFLICT (user_id, identifier)
      DO UPDATE SET
        use_count        = payments.recipients.use_count + 1,
        nickname         = COALESCE(EXCLUDED.nickname, payments.recipients.nickname),
        default_category = COALESCE(EXCLUDED.default_category, payments.recipients.default_category),
        updated_at       = NOW()
      """;

    Tuple params = Tuple.of(
      UUID.fromString(recipient.getString("userId")),
      recipient.getString("identifier"),
      recipient.getString("recipientType"),
      recipient.getString("nickname"),
      recipient.getString("category")
    );

    return pool.preparedQuery(sql).execute(params).mapEmpty();
  }

  public Future<JsonArray> getRecipients(String userId) {
    String sql = """
      SELECT id, identifier, recipient_type, nickname, default_category, use_count
        FROM payments.recipients
       WHERE user_id = $1
       ORDER BY use_count DESC
      """;

    return pool.preparedQuery(sql)
      .execute(Tuple.of(UUID.fromString(userId)))
      .map(this::rowsToJsonArray);
  }

  public Future<Void> updateRecipient(String recipientId, String userId, JsonObject updates) {
    String sql = """
      UPDATE payments.recipients
         SET nickname         = COALESCE($1, nickname),
             default_category = COALESCE($2, default_category),
             updated_at       = NOW()
       WHERE id = $3 AND user_id = $4
      """;

    Tuple params = Tuple.of(
      updates.getString("nickname"),
      updates.getString("defaultCategory"),
      UUID.fromString(recipientId),
      UUID.fromString(userId)
    );

    return pool.preparedQuery(sql).execute(params).mapEmpty();
  }

  // -------------------------------------------------------------------------
  // Row mapping helpers
  // -------------------------------------------------------------------------

  private JsonArray rowsToJsonArray(RowSet<Row> rows) {
    JsonArray result = new JsonArray();
    // Vert.x 5: RowSet<Row> implements Iterable<Row> — forEach works fine
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
