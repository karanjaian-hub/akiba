package com.akiba.notification.handlers;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.UUID;

public class AlertHandler {

  private final Pool pgPool;

  public AlertHandler(Pool pgPool) {
    this.pgPool = pgPool;
  }

  public Future<JsonObject> saveAlert(UUID userId, String type, String title, String body) {
    String sql = """
            INSERT INTO notifications.alerts (user_id, type, title, body)
            VALUES ($1, $2, $3, $4)
            RETURNING id, user_id, type, title, body, is_read, created_at
        """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(userId, type, title, body))
      .map(rows -> {
        var row = rows.iterator().next();
        return new JsonObject()
          .put("id",         row.getUUID("id").toString())
          .put("user_id",    row.getUUID("user_id").toString())
          .put("type",       row.getString("type"))
          .put("title",      row.getString("title"))
          .put("body",       row.getString("body"))
          .put("is_read",    row.getBoolean("is_read"))
          .put("created_at", row.getLocalDateTime("created_at").toString());
      });
  }

  public Future<JsonObject> getAlerts(UUID userId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    String sql = """
            SELECT id, type, title, body, is_read, created_at
            FROM notifications.alerts
            WHERE user_id = $1
            ORDER BY is_read ASC, created_at DESC
            LIMIT $2 OFFSET $3
        """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(userId, pageSize, offset))
      .map(rows -> {
        var alerts = new JsonArray();
        rows.forEach(row -> alerts.add(new JsonObject()
          .put("id",         row.getUUID("id").toString())
          .put("type",       row.getString("type"))
          .put("title",      row.getString("title"))
          .put("body",       row.getString("body"))
          .put("is_read",    row.getBoolean("is_read"))
          .put("created_at", row.getLocalDateTime("created_at").toString())
        ));
        return new JsonObject()
          .put("alerts",    alerts)
          .put("page",      page)
          .put("page_size", pageSize);
      });
  }

  public Future<Void> markOneRead(UUID alertId, UUID userId) {
    String sql = """
            UPDATE notifications.alerts
            SET is_read = true
            WHERE id = $1 AND user_id = $2
        """;
    return pgPool.preparedQuery(sql)
      .execute(Tuple.of(alertId, userId))
      .mapEmpty();
  }

  public Future<Void> markAllRead(UUID userId) {
    return pgPool.preparedQuery(
      "UPDATE notifications.alerts SET is_read = true WHERE user_id = $1"
    ).execute(Tuple.of(userId)).mapEmpty();
  }

  public Future<JsonObject> getUnreadCount(UUID userId) {
    return pgPool.preparedQuery(
        "SELECT COUNT(*) AS count FROM notifications.alerts WHERE user_id = $1 AND is_read = false"
      ).execute(Tuple.of(userId))
      .map(rows -> new JsonObject()
        .put("count", rows.iterator().next().getLong("count")));
  }
}
