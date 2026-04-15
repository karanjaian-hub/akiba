package com.akiba.notification.repositories;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class PreferencesRepository {

  private final Pool pgPool;

  public PreferencesRepository(Pool pgPool) {
    this.pgPool = pgPool;
  }

  public Future<JsonObject> getPreferences(UUID userId) {
    return pgPool.preparedQuery(
        "SELECT * FROM notifications.preferences WHERE user_id = $1"
      ).execute(Tuple.of(userId))
      .map(rows -> {
        if (!rows.iterator().hasNext()) {
          return defaultPreferences();
        }
        var row = rows.iterator().next();
        return new JsonObject()
          .put("push_token",      row.getString("push_token"))
          .put("payment_enabled", row.getBoolean("payment_enabled"))
          .put("budget_enabled",  row.getBoolean("budget_enabled"))
          .put("savings_enabled", row.getBoolean("savings_enabled"))
          .put("reports_enabled", row.getBoolean("reports_enabled"))
          .put("system_enabled",  row.getBoolean("system_enabled"));
      });
  }

  public Future<Void> upsertPreferences(UUID userId, JsonObject prefs) {
    String sql = """
            INSERT INTO notifications.preferences
                (user_id, push_token, payment_enabled, budget_enabled,
                 savings_enabled, reports_enabled, system_enabled, updated_at)
            VALUES ($1,$2,$3,$4,$5,$6,$7,NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                push_token       = EXCLUDED.push_token,
                payment_enabled  = EXCLUDED.payment_enabled,
                budget_enabled   = EXCLUDED.budget_enabled,
                savings_enabled  = EXCLUDED.savings_enabled,
                reports_enabled  = EXCLUDED.reports_enabled,
                system_enabled   = EXCLUDED.system_enabled,
                updated_at       = NOW()
        """;
    return pgPool.preparedQuery(sql).execute(Tuple.of(
      userId,
      prefs.getString("push_token"),
      prefs.getBoolean("payment_enabled",  true),
      prefs.getBoolean("budget_enabled",   true),
      prefs.getBoolean("savings_enabled",  true),
      prefs.getBoolean("reports_enabled",  true),
      prefs.getBoolean("system_enabled",   true)
    )).mapEmpty();
  }

  private JsonObject defaultPreferences() {
    return new JsonObject()
      .put("push_token",      (String) null)
      .put("payment_enabled", true)
      .put("budget_enabled",  true)
      .put("savings_enabled", true)
      .put("reports_enabled", true)
      .put("system_enabled",  true);
  }
}
