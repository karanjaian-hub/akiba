package com.akiba.parsing.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class GroqClient {

  private static final String GROQ_PATH = "/openai/v1/chat/completions"; // Groq uses OpenAI-compatible endpoints
  private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

  private static final int  MAX_RETRIES    = 3;
  private static final long RETRY_DELAY_MS = 2000L;

  private final WebClient httpClient;
  private final String    apiKey;
  private final Vertx     vertx;

  public GroqClient(Vertx vertx) {
    this.vertx      = vertx;
    this.httpClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setDefaultHost("api.groq.com")
      .setDefaultPort(443));

    this.apiKey = System.getenv("GROQ_API_KEY");
    if (this.apiKey == null || this.apiKey.isBlank()) {
      throw new IllegalStateException("GROQ_API_KEY env var is not set");
    }
  }

  public Future<String> ask(String prompt) {
    return askWithRetry(prompt, 1);
  }

  private Future<String> askWithRetry(String prompt, int attempt) {
    JsonObject body = buildRequestBody(prompt);

    return httpClient
      .post(GROQ_PATH)
      .putHeader("Authorization", "Bearer " + apiKey)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .compose(response -> {

        if (response.statusCode() == 200) {
          return Future.succeededFuture(extractText(response.bodyAsJsonObject()));
        }

        if (response.statusCode() == 503 && attempt < MAX_RETRIES) {
          System.out.printf(
            "[GroqClient] 503 received, retrying in %dms (attempt %d/%d)%n",
            RETRY_DELAY_MS, attempt, MAX_RETRIES
          );
          return vertx.timer(RETRY_DELAY_MS)
            .compose(v -> askWithRetry(prompt, attempt + 1));
        }

        return Future.failedFuture(
          "Groq API error: HTTP " + response.statusCode()
            + " (after " + attempt + " attempt(s)) — "
            + response.bodyAsString()
        );
      });
  }

  private JsonObject buildRequestBody(String prompt) {
    return new JsonObject()
      .put("model", GROQ_MODEL)
      .put("messages", new JsonArray()
        .add(new JsonObject()
          .put("role", "user")
          .put("content", prompt)))
      .put("temperature", 0.1); // low temperature = more consistent JSON output
  }

  private String extractText(JsonObject responseBody) {
    try {
      return responseBody
        .getJsonArray("choices")
        .getJsonObject(0)
        .getJsonObject("message")
        .getString("content", "");
    } catch (Exception e) {
      throw new RuntimeException("Unexpected Groq response shape: " + responseBody, e);
    }
  }
}
