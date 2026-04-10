package com.akiba.ai.providers;

import com.akiba.ai.models.Message;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * GeminiProvider sends prompts to Google's Gemini 1.5-flash model
 * via the REST GenerateContent endpoint.
 *
 * Endpoint shape:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=API_KEY
 *   Body: { "system_instruction": {...}, "contents": [{role, parts:[{text}]}, ...] }
 *
 * We don't need any extra headers — the API key goes in the query param.
 */
public class GeminiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

  private static final String GEMINI_HOST = "generativelanguage.googleapis.com";
  private static final String GEMINI_PATH =
    "/v1beta/models/gemini-1.5-flash:generateContent";

  private final WebClient webClient;
  private final String apiKey;

  public GeminiProvider(WebClient webClient, String apiKey) {
    this.webClient = webClient;
    this.apiKey    = apiKey;
  }

  @Override
  public Future<String> complete(String systemPrompt, String userMessage, List<Message> history) {
    JsonObject body = buildRequestBody(systemPrompt, userMessage, history);

    return webClient
      .postAbs("https://" + GEMINI_HOST + GEMINI_PATH)
      .addQueryParam("key", apiKey)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .compose(response -> {
        if (response.statusCode() != 200) {
          String err = "Gemini API error " + response.statusCode() + ": " + response.bodyAsString();
          log.error(err);
          return Future.failedFuture(err);
        }
        return Future.succeededFuture(extractReplyText(response.bodyAsJsonObject()));
      });
  }

  /**
   * Builds the JSON body Gemini expects.
   *
   * Gemini's "contents" array holds all turns in chronological order.
   * The system prompt goes in a separate "system_instruction" field —
   * NOT as an extra content turn, which is a common mistake.
   */
  private JsonObject buildRequestBody(String systemPrompt, String userMessage, List<Message> history) {
    JsonArray contents = new JsonArray();

    // Replay conversation history so Gemini has context.
    for (Message msg : history) {
      contents.add(new JsonObject()
        .put("role", msg.role)
        .put("parts", new JsonArray().add(new JsonObject().put("text", msg.content)))
      );
    }

    // Always append the current user turn last.
    contents.add(new JsonObject()
      .put("role", "user")
      .put("parts", new JsonArray().add(new JsonObject().put("text", userMessage)))
    );

    return new JsonObject()
      .put("system_instruction", new JsonObject()
        .put("parts", new JsonArray().add(new JsonObject().put("text", systemPrompt)))
      )
      .put("contents", contents);
  }

  /**
   * Drills into Gemini's nested response to pull out the reply text.
   * Path: candidates[0].content.parts[0].text
   */
  private String extractReplyText(JsonObject responseBody) {
    return responseBody
      .getJsonArray("candidates")
      .getJsonObject(0)
      .getJsonObject("content")
      .getJsonArray("parts")
      .getJsonObject(0)
      .getString("text");
  }
}
