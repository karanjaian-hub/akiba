package com.akiba.ai.providers;

import com.akiba.ai.models.Message;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GroqProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);

  private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

  private static final String MODEL = "llama-3.3-70b-versatile";

  private final WebClient webClient;
  private final String    apiKey;

  public GroqProvider(WebClient webClient, String apiKey) {
    this.webClient = webClient;
    this.apiKey    = apiKey;
  }

  @Override
  public Future<String> complete(String systemPrompt, String userMessage, List<Message> history) {
    JsonObject body = buildRequestBody(systemPrompt, userMessage, history);

    return webClient
      .postAbs(GROQ_URL)
      // API key goes in the Authorization header — NOT a query param like Gemini.
      .putHeader("Authorization", "Bearer " + apiKey)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .compose(response -> {
        if (response.statusCode() != 200) {
          String err = "Groq API error " + response.statusCode() + ": " + response.bodyAsString();
          log.error(err);
          return Future.failedFuture(err);
        }
        return Future.succeededFuture(extractReplyText(response.bodyAsJsonObject()));
      });
  }

  private JsonObject buildRequestBody(String systemPrompt, String userMessage, List<Message> history) {
    JsonArray messages = new JsonArray();

    // System message always goes first — sets the AI's persona and context.
    messages.add(new JsonObject()
      .put("role",    "system")
      .put("content", systemPrompt)
    );

    // Replay conversation history so Groq remembers prior turns.
    for (Message msg : history) {
      // Map "model" → "assistant" (Groq uses OpenAI convention, not Gemini's)
      String role = msg.role.equals("model") ? "assistant" : msg.role;
      messages.add(new JsonObject()
        .put("role",    role)
        .put("content", msg.content)
      );
    }

    // Current user message always goes last.
    messages.add(new JsonObject()
      .put("role",    "user")
      .put("content", userMessage)
    );

    return new JsonObject()
      .put("model",       MODEL)
      .put("messages",    messages)
      // max_tokens caps the reply length — 1024 is generous for financial advice.
      .put("max_tokens",  1024)
      // temperature 0.7: balanced between creative and factual.
      // 0.0 = very literal/repetitive, 1.0 = very creative/unpredictable.
      .put("temperature", 0.7);
  }

   private String extractReplyText(JsonObject responseBody) {
    return responseBody
      .getJsonArray("choices")
      .getJsonObject(0)
      .getJsonObject("message")
      .getString("content");
  }
}
