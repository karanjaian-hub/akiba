package com.akiba.payment.repositories;

import com.akiba.payment.models.Payment;
import com.akiba.payment.models.SavedRecipient;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All SQL lives here — handlers never touch the DB directly.
 * Single Responsibility: translate between Java objects and database rows.
 */
public class PaymentRepository {

  private final Pool db;

  public PaymentRepository(Pool db) {
    this.db = db;
  }

  // ── Payments ───────────────────────────────────────────────────────────────

  /** Insert a fresh PENDING payment and return it with the generated ID. */
  public Future<Payment> insertPayment(Payment payment) {
    String sql = """
            INSERT INTO payments.records
                (user_id, phone, account_ref, amount, category, type, status,
                 checkout_request_id, merchant_request_id)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            RETURNING id, created_at, updated_at
            """;

    Tuple params = Tuple.of(
      payment.getUserId(),
      payment.getPhone(),
      payment.getAccountRef(),
      payment.getAmount(),
      payment.getCategory(),
      payment.getType().name(),
      payment.getStatus().name(),
      payment.getCheckoutRequestId(),
      payment.getMerchantRequestId()
    );

    return db.preparedQuery(sql)
      .execute(params)
      .map(rows -> {
        Row row = rows.iterator().next();
        payment.setId(row.getUUID("id"));
        payment.setCreatedAt(row.getLocalDateTime("created_at"));
        payment.setUpdatedAt(row.getLocalDateTime("updated_at"));
        return payment;
      });
  }

  /**
   * Called by the Daraja callback to update a payment's final state.
   * ResultCode "0" = success; anything else = failure.
   */
  public Future<Void> updatePaymentStatus(
    String checkoutRequestId, Payment.Status status,
    String resultCode, String resultDesc) {

    String sql = """
            UPDATE payments.records
            SET status = $1, result_code = $2, result_desc = $3, updated_at = NOW()
            WHERE checkout_request_id = $4
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(status.name(), resultCode, resultDesc, checkoutRequestId))
      .mapEmpty();
  }

  public Future<Payment> findPaymentById(UUID paymentId, UUID userId) {
    String sql = """
            SELECT * FROM payments.records
            WHERE id = $1 AND user_id = $2
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(paymentId, userId))
      .map(rows -> {
        if (!rows.iterator().hasNext()) return null;
        return rowToPayment(rows.iterator().next());
      });
  }

  /** Find a payment by Daraja's checkout ID — used to locate the record during callback. */
  public Future<Payment> findByCheckoutRequestId(String checkoutRequestId) {
    String sql = "SELECT * FROM payments.records WHERE checkout_request_id = $1";
    return db.preparedQuery(sql)
      .execute(Tuple.of(checkoutRequestId))
      .map(rows -> {
        if (!rows.iterator().hasNext()) return null;
        return rowToPayment(rows.iterator().next());
      });
  }

  /** Paginated payment history for the authenticated user — newest first. */
  public Future<List<Payment>> findPaymentHistory(UUID userId, int page, int size) {
    String sql = """
            SELECT * FROM payments.records
            WHERE user_id = $1
            ORDER BY created_at DESC
            LIMIT $2 OFFSET $3
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(userId, size, (page - 1) * size))
      .map(rows -> {
        List<Payment> payments = new ArrayList<>();
        rows.forEach(row -> payments.add(rowToPayment(row)));
        return payments;
      });
  }

  // ── Recipients ─────────────────────────────────────────────────────────────

  /**
   * Upsert — if the user has paid this identifier before, increment use_count.
   * Otherwise insert a new row. This keeps the "recent recipients" list accurate.
   */
  public Future<Void> upsertRecipient(UUID userId, String identifier, String nickname,
                                      String category, String type) {
    String sql = """
            INSERT INTO payments.recipients (user_id, identifier, nickname, default_category, type)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (user_id, identifier)
            DO UPDATE SET
                use_count        = payments.recipients.use_count + 1,
                last_used_at     = NOW(),
                nickname         = COALESCE(EXCLUDED.nickname, payments.recipients.nickname),
                default_category = COALESCE(EXCLUDED.default_category, payments.recipients.default_category)
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(userId, identifier, nickname, category, type))
      .mapEmpty();
  }

  /** Returns saved recipients sorted by use_count — so frequently used ones appear first. */
  public Future<List<SavedRecipient>> findRecipients(UUID userId) {
    String sql = """
            SELECT * FROM payments.recipients
            WHERE user_id = $1
            ORDER BY use_count DESC, last_used_at DESC
            LIMIT 20
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(userId))
      .map(rows -> {
        List<SavedRecipient> recipients = new ArrayList<>();
        rows.forEach(row -> recipients.add(rowToRecipient(row)));
        return recipients;
      });
  }

  public Future<Void> updateRecipient(UUID recipientId, UUID userId, String nickname, String category) {
    String sql = """
            UPDATE payments.recipients
            SET nickname = COALESCE($1, nickname),
                default_category = COALESCE($2, default_category)
            WHERE id = $3 AND user_id = $4
            """;

    return db.preparedQuery(sql)
      .execute(Tuple.of(nickname, category, recipientId, userId))
      .mapEmpty();
  }

  // ── Row mappers ────────────────────────────────────────────────────────────

  private Payment rowToPayment(Row row) {
    Payment p = new Payment();
    p.setId(row.getUUID("id"));
    p.setUserId(row.getUUID("user_id"));
    p.setPhone(row.getString("phone"));
    p.setAccountRef(row.getString("account_ref"));
    p.setAmount(row.getBigDecimal("amount"));
    p.setCategory(row.getString("category"));
    p.setType(Payment.Type.valueOf(row.getString("type")));
    p.setStatus(Payment.Status.valueOf(row.getString("status")));
    p.setCheckoutRequestId(row.getString("checkout_request_id"));
    p.setMerchantRequestId(row.getString("merchant_request_id"));
    p.setResultCode(row.getString("result_code"));
    p.setResultDesc(row.getString("result_desc"));
    p.setCreatedAt(row.getLocalDateTime("created_at"));
    p.setUpdatedAt(row.getLocalDateTime("updated_at"));
    return p;
  }

  private SavedRecipient rowToRecipient(Row row) {
    SavedRecipient r = new SavedRecipient();
    r.setId(row.getUUID("id"));
    r.setUserId(row.getUUID("user_id"));
    r.setIdentifier(row.getString("identifier"));
    r.setNickname(row.getString("nickname"));
    r.setDefaultCategory(row.getString("default_category"));
    r.setType(SavedRecipient.Type.valueOf(row.getString("type")));
    r.setUseCount(row.getInteger("use_count"));
    r.setLastUsedAt(row.getLocalDateTime("last_used_at"));
    r.setCreatedAt(row.getLocalDateTime("created_at"));
    return r;
  }
}
