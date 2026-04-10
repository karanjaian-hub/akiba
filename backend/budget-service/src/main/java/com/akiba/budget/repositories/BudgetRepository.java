package com.akiba.budget.repositories;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;  // ← changed
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class BudgetRepository {

  private final Pool pgPool;  // ← changed

  public BudgetRepository(Pool pgPool) {  // ← changed
    this.pgPool = pgPool;
  }


  /**
     * Upserts a category limit — INSERT on first set, UPDATE on change.
     * ON CONFLICT targets the unique(user_id, category) constraint.
     */
    public Future<Void> upsertLimit(String userId, String category, double monthlyLimit) {
        String sql = """
            INSERT INTO budgets.limits (user_id, category, monthly_limit, updated_at)
            VALUES ($1, $2, $3, NOW())
            ON CONFLICT (user_id, category)
            DO UPDATE SET monthly_limit = $3, updated_at = NOW()
            """;
        return pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId), category, monthlyLimit))
            .mapEmpty();
    }

    /**
     * Adds the payment amount to this month's spend for a category.
     * Uses upsert so the first payment in a category auto-creates the row.
     */
    public Future<Void> addSpend(String userId, String category, double amount, int month, int year) {
        String sql = """
            INSERT INTO budgets.spend_tracking (user_id, category, month, year, amount_spent, updated_at)
            VALUES ($1, $2, $3, $4, $5, NOW())
            ON CONFLICT (user_id, category, month, year)
            DO UPDATE SET
                amount_spent = budgets.spend_tracking.amount_spent + $5,
                updated_at   = NOW()
            """;
        return pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId), category, month, year, amount))
            .mapEmpty();
    }

    /**
     * Returns all limits joined with current month's spend.
     * LEFT JOIN means categories with a limit but no spend yet still appear.
     */
    public Future<JsonArray> getAllWithSpend(String userId, int month, int year) {
        String sql = """
            SELECT
                l.category,
                l.monthly_limit,
                COALESCE(s.amount_spent, 0) AS amount_spent
            FROM budgets.limits l
            LEFT JOIN budgets.spend_tracking s
                ON  s.user_id  = l.user_id
                AND s.category = l.category
                AND s.month    = $2
                AND s.year     = $3
            WHERE l.user_id = $1
            ORDER BY l.category
            """;
        return pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId), month, year))
            .map(rows -> {
                JsonArray result = new JsonArray();
                for (Row row : rows) {
                    result.add(rowToJson(row));
                }
                return result;
            });
    }

    /**
     * Returns a single category's limit and current spend.
     * Used by the budget check endpoint before a payment.
     */
    public Future<JsonObject> getOneCategorySpend(String userId, String category, int month, int year) {
        String sql = """
            SELECT
                l.monthly_limit,
                COALESCE(s.amount_spent, 0) AS amount_spent
            FROM budgets.limits l
            LEFT JOIN budgets.spend_tracking s
                ON  s.user_id  = l.user_id
                AND s.category = l.category
                AND s.month    = $3
                AND s.year     = $4
            WHERE l.user_id  = $1
              AND l.category = $2
            """;
        return pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId), category, month, year))
            .map(rows -> {
                if (!rows.iterator().hasNext()) return null;
                Row row = rows.iterator().next();
                return new JsonObject()
                    .put("monthlyLimit",  row.getDouble("monthly_limit"))
                    .put("amountSpent",   row.getDouble("amount_spent"));
            });
    }

    private JsonObject rowToJson(Row row) {
        double limit   = row.getDouble("monthly_limit");
        double spent   = row.getDouble("amount_spent");
        double remaining  = Math.max(0, limit - spent);
        int    percentage = limit > 0 ? (int) ((spent / limit) * 100) : 0;

        return new JsonObject()
            .put("category",   row.getString("category"))
            .put("limit",      limit)
            .put("spent",      spent)
            .put("remaining",  remaining)
            .put("percentage", percentage);
    }
}
