package com.akiba.ai.repositories;

import com.akiba.ai.models.Conversation;
import com.akiba.ai.models.Message;
import com.akiba.ai.models.Report;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AiRepository owns every SQL statement for the ai.* schema.
 *
 * Single-responsibility: this class does nothing but talk to the
 * database. Business logic lives in the service/handler layers.
 *
 * NOTE: Uses io.vertx.sqlclient.Pool (the Vert.x 5 unified interface)
 * instead of the old PgPool — PgPool still exists but Pool is the
 * preferred type for injection so tests can swap in any driver.
 */
public class AiRepository {

  private static final Logger log = LoggerFactory.getLogger(AiRepository.class);

  private final Pool pool;

  public AiRepository(Pool pool) {
    this.pool = pool;
  }

  // ── Conversations ──────────────────────────────────────────────────────────

  public Future<Conversation> createConversation(UUID userId, String title) {
    return pool.preparedQuery(
        "INSERT INTO ai.conversations (id, user_id, title, created_at, updated_at) " +
          "VALUES ($1, $2, $3, NOW(), NOW()) RETURNING *"
      ).execute(Tuple.of(UUID.randomUUID(), userId, title))
      .map(rows -> rowToConversation(rows.iterator().next()));
  }

  public Future<List<Conversation>> findConversationsByUser(UUID userId) {
    return pool.preparedQuery(
        "SELECT * FROM ai.conversations WHERE user_id = $1 ORDER BY updated_at DESC"
      ).execute(Tuple.of(userId))
      .map(rows -> {
        List<Conversation> list = new ArrayList<>();
        for (Row row : rows) {
          list.add(rowToConversation(row));
        }
        return list;
      });
  }

  // ── Messages ───────────────────────────────────────────────────────────────

  public Future<Message> saveMessage(UUID conversationId, String role, String content) {
    return pool.preparedQuery(
        "INSERT INTO ai.messages (id, conversation_id, role, content, created_at) " +
          "VALUES ($1, $2, $3, $4, NOW()) RETURNING *"
      ).execute(Tuple.of(UUID.randomUUID(), conversationId, role, content))
      .map(rows -> rowToMessage(rows.iterator().next()));
  }

  /**
   * Fetches the most recent N messages for a conversation.
   * We cap at 20 so we don't blow Gemini's context window or inflate costs.
   */
  public Future<List<Message>> findRecentMessages(UUID conversationId, int limit) {
    return pool.preparedQuery(
        "SELECT * FROM (" +
          "  SELECT * FROM ai.messages WHERE conversation_id = $1 " +
          "  ORDER BY created_at DESC LIMIT $2" +
          ") sub ORDER BY created_at ASC"
      ).execute(Tuple.of(conversationId, limit))
      .map(rows -> {
        List<Message> list = new ArrayList<>();
        for (Row row : rows) {
          list.add(rowToMessage(row));
        }
        return list;
      });
  }

  // ── Reports ────────────────────────────────────────────────────────────────

  public Future<Report> createReport(UUID userId, int month, int year) {
    return pool.preparedQuery(
        "INSERT INTO ai.reports (id, user_id, month, year, status, created_at) " +
          "VALUES ($1, $2, $3, $4, 'GENERATING', NOW()) RETURNING *"
      ).execute(Tuple.of(UUID.randomUUID(), userId, month, year))
      .map(rows -> rowToReport(rows.iterator().next()));
  }

  public Future<Void> updateReportContent(UUID reportId, String content, String status) {
    return pool.preparedQuery(
        "UPDATE ai.reports SET content = $1, status = $2 WHERE id = $3"
      ).execute(Tuple.of(content, status, reportId))
      .mapEmpty();
  }

  public Future<Report> findReport(UUID userId, int month, int year) {
    return pool.preparedQuery(
        "SELECT * FROM ai.reports WHERE user_id = $1 AND month = $2 AND year = $3"
      ).execute(Tuple.of(userId, month, year))
      .map(rows -> rows.rowCount() == 0 ? null : rowToReport(rows.iterator().next()));
  }

  // ── Row mappers ────────────────────────────────────────────────────────────

  private Conversation rowToConversation(Row row) {
    Conversation c = new Conversation();
    c.id        = row.getUUID("id");
    c.userId    = row.getUUID("user_id");
    c.title     = row.getString("title");
    c.createdAt = row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC);
    c.updatedAt = row.getLocalDateTime("updated_at").toInstant(ZoneOffset.UTC);
    return c;
  }

  private Message rowToMessage(Row row) {
    Message m = new Message();
    m.id             = row.getUUID("id");
    m.conversationId = row.getUUID("conversation_id");
    m.role           = row.getString("role");
    m.content        = row.getString("content");
    m.createdAt      = row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC);
    return m;
  }

  private Report rowToReport(Row row) {
    Report r = new Report();
    r.id        = row.getUUID("id");
    r.userId    = row.getUUID("user_id");
    r.month     = row.getInteger("month");
    r.year      = row.getInteger("year");
    r.content   = row.getString("content");
    r.status    = row.getString("status");
    r.createdAt = row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC);
    return r;
  }
}
