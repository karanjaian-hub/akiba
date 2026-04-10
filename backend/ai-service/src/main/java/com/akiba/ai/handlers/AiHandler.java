package com.akiba.ai.handlers;

import com.akiba.ai.services.AiService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * AiHandler contains one method per HTTP route.
 *
 * Each method follows the same pattern:
 *   1. Extract inputs (user ID from JWT context, body, path params).
 *   2. Call AiService.
 *   3. Map success → 200/201 JSON, failure → appropriate error code.
 *
 * We never write SQL or cache logic here — that's why we have AiService.
 */
public class AiHandler {

  private static final Logger log = LoggerFactory.getLogger(AiHandler.class);

  private final AiService aiService;

  public AiHandler(AiService aiService) {
    this.aiService = aiService;
  }

  /**
   * POST /ai/chat
   * Body: { "message": "...", "conversationId": "..." (optional) }
   *
   * conversationId absent = start a new conversation.
   * conversationId present = continue an existing one.
   */
  public void chat(RoutingContext ctx) {
    UUID   userId = extractUserId(ctx);
    String token  = extractToken(ctx);

    JsonObject body = ctx.body().asJsonObject();
    if (body == null || !body.containsKey("message")) {
      ctx.response().setStatusCode(400).end(error("'message' field is required"));
      return;
    }

    String userMessage     = body.getString("message").trim();
    String conversationStr = body.getString("conversationId");  // may be null
    UUID   conversationId  = conversationStr != null ? UUID.fromString(conversationStr) : null;

    if (userMessage.isBlank()) {
      ctx.response().setStatusCode(400).end(error("Message cannot be empty"));
      return;
    }

    aiService.chat(userId, conversationId, userMessage, token)
      .onSuccess(result -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject()
          .put("conversationId", result.conversationId().toString())
          .put("reply", result.reply())
          .encode()
        )
      )
      .onFailure(err -> handleError(ctx, "Chat failed", err));
  }

  /**
   * GET /ai/conversations
   * Returns all conversations for the authenticated user.
   */
  public void getConversations(RoutingContext ctx) {
    UUID userId = extractUserId(ctx);

    aiService.getConversations(userId)
      .onSuccess(conversations -> {
        JsonArray arr = new JsonArray();
        conversations.forEach(c -> arr.add(new JsonObject()
          .put("id",        c.id.toString())
          .put("title",     c.title)
          .put("createdAt", c.createdAt.toString())
          .put("updatedAt", c.updatedAt.toString())
        ));
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("conversations", arr).encode());
      })
      .onFailure(err -> handleError(ctx, "Could not fetch conversations", err));
  }

  /**
   * GET /ai/reports/:month/:year
   * Returns the AI-generated report for a given month/year.
   */
  public void getReport(RoutingContext ctx) {
    UUID userId = extractUserId(ctx);

    int month, year;
    try {
      month = Integer.parseInt(ctx.pathParam("month"));
      year  = Integer.parseInt(ctx.pathParam("year"));
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end(error("Invalid month or year"));
      return;
    }

    aiService.getReport(userId, month, year)
      .onSuccess(report -> {
        if (report == null) {
          ctx.response().setStatusCode(404).end(error("Report not found"));
          return;
        }
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject()
            .put("id",        report.id.toString())
            .put("month",     report.month)
            .put("year",      report.year)
            .put("status",    report.status)
            .put("content",   report.content)
            .put("createdAt", report.createdAt.toString())
            .encode()
          );
      })
      .onFailure(err -> handleError(ctx, "Could not fetch report", err));
  }

  /**
   * POST /ai/insights
   * Body: { "question": "..." }
   *
   * Quick one-off question — no conversation stored, no history.
   * Great for the home screen "Ask Akiba" widget.
   */
  public void quickInsight(RoutingContext ctx) {
    UUID   userId = extractUserId(ctx);
    String token  = extractToken(ctx);

    JsonObject body = ctx.body().asJsonObject();
    if (body == null || !body.containsKey("question")) {
      ctx.response().setStatusCode(400).end(error("'question' field is required"));
      return;
    }

    String question = body.getString("question").trim();
    if (question.isBlank()) {
      ctx.response().setStatusCode(400).end(error("Question cannot be empty"));
      return;
    }

    aiService.quickInsight(userId, question, token)
      .onSuccess(reply -> ctx.response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("insight", reply).encode())
      )
      .onFailure(err -> handleError(ctx, "Insight generation failed", err));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * The API Gateway validates the JWT and forwards the userId as a
   * header "X-User-Id" to every downstream service.
   */
  private UUID extractUserId(RoutingContext ctx) {
    return UUID.fromString(ctx.request().getHeader("X-User-Id"));
  }

  /**
   * We forward the raw Authorization header to internal services so
   * they can authenticate our internal requests.
   */
  private String extractToken(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    return authHeader != null ? authHeader.replace("Bearer ", "") : "";
  }

  private void handleError(RoutingContext ctx, String message, Throwable err) {
    log.error("{}: {}", message, err.getMessage());
    ctx.response()
      .setStatusCode(500)
      .putHeader("Content-Type", "application/json")
      .end(error(message));
  }

  private String error(String message) {
    return new JsonObject().put("error", message).encode();
  }
}
