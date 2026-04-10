package com.akiba.ai.services;

import com.akiba.ai.models.Conversation;
import com.akiba.ai.models.Message;
import com.akiba.ai.models.Report;
import com.akiba.ai.providers.AiProvider;
import com.akiba.ai.repositories.AiRepository;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * AiService is the central brain of the AI Service.
 *
 * It orchestrates:
 *   1. Cache check (AiCacheService) — hit? return early.
 *   2. Financial snapshot fetch (FinancialContextService) — build system prompt.
 *   3. Conversation history fetch (AiRepository) — give Gemini context.
 *   4. AI call (AiProvider / GeminiProvider) — get the reply.
 *   5. Persist both turns and update cache.
 *
 * This is the ONLY class that talks to all the others. Handlers only
 * ever call AiService methods — they never touch the cache or DB directly.
 */
public class AiService {

  private static final Logger log = LoggerFactory.getLogger(AiService.class);

  // Max history messages sent to Gemini per request.
  // More context = better answers but higher latency + cost.
  private static final int MAX_HISTORY = 20;

  private final AiProvider              aiProvider;
  private final AiCacheService          cacheService;
  private final FinancialContextService contextService;
  private final AiRepository            repository;

  public AiService(
    AiProvider aiProvider,
    AiCacheService cacheService,
    FinancialContextService contextService,
    AiRepository repository
  ) {
    this.aiProvider     = aiProvider;
    this.cacheService   = cacheService;
    this.contextService = contextService;
    this.repository     = repository;
  }

  /**
   * Full chat flow: history-aware, financially contextualised.
   *
   * @param userId         Extracted from the JWT by the handler.
   * @param conversationId Null = start a new conversation.
   * @param userMessage    What the user typed.
   * @param authToken      Forwarded to internal services so they can authenticate the request.
   * @return The AI's reply text.
   */
  public Future<ChatResult> chat(UUID userId, UUID conversationId, String userMessage, String authToken) {

    // Step 1: Resolve or create the conversation.
    Future<Conversation> conversationFuture = (conversationId != null)
      ? Future.succeededFuture(existingConversation(conversationId))
      : repository.createConversation(userId, truncate(userMessage, 60));

    return conversationFuture.compose(conversation -> {

      // Step 2: Build the financial system prompt and fetch history in parallel.
      Future<String>        systemPromptFuture = contextService.buildSystemPrompt(userId.toString(), authToken);
      Future<List<Message>> historyFuture      = repository.findRecentMessages(conversation.id, MAX_HISTORY);

      return Future.all(systemPromptFuture, historyFuture)
        .compose(cf -> {
          String        systemPrompt = cf.resultAt(0);
          List<Message> history      = cf.resultAt(1);

          // Step 3: Cache check — skip Gemini if we've seen this exact prompt.
          return cacheService.get(systemPrompt, userMessage)
            .compose(cached -> {
              if (cached != null) {
                log.debug("Returning cached AI response for user {}", userId);
                return persistAndReturn(conversation, userMessage, cached);
              }
              // Step 4: Cache miss — call Gemini.
              return aiProvider.complete(systemPrompt, userMessage, history)
                .compose(reply ->
                  cacheService.set(systemPrompt, userMessage, reply)
                    .compose(v -> persistAndReturn(conversation, userMessage, reply))
                );
            });
        });
    });
  }

  /**
   * One-off insight: no conversation history, no DB persistence.
   * Used by the POST /ai/insights endpoint for quick single-turn queries.
   */
  public Future<String> quickInsight(UUID userId, String question, String authToken) {
    return contextService.buildSystemPrompt(userId.toString(), authToken)
      .compose(systemPrompt -> cacheService.get(systemPrompt, question)
        .compose(cached -> {
          if (cached != null) return Future.succeededFuture(cached);
          return aiProvider.complete(systemPrompt, question, List.of())
            .compose(reply ->
              cacheService.set(systemPrompt, question, reply)
                .map(v -> reply)
            );
        })
      );
  }

  public Future<List<Conversation>> getConversations(UUID userId) {
    return repository.findConversationsByUser(userId);
  }

  public Future<Report> getReport(UUID userId, int month, int year) {
    return repository.findReport(userId, month, year);
  }

  /**
   * Saves both user and AI message turns, then returns the structured result.
   */
  private Future<ChatResult> persistAndReturn(Conversation conversation, String userMessage, String aiReply) {
    return repository.saveMessage(conversation.id, "user", userMessage)
      .compose(userMsg -> repository.saveMessage(conversation.id, "model", aiReply))
      .map(aiMsg -> new ChatResult(conversation.id, aiReply));
  }

  /**
   * Lightweight stub so we can pass a conversationId without a DB lookup
   * when the conversation already exists. The handler already validated it.
   */
  private Conversation existingConversation(UUID conversationId) {
    Conversation c = new Conversation();
    c.id = conversationId;
    return c;
  }

  private String truncate(String text, int maxLen) {
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
  }

  // Simple value object returned to the handler.
  public record ChatResult(UUID conversationId, String reply) {}
}
