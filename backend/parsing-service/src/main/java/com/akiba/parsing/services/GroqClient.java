package com.akiba.parsing.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class GroqClient {

  private static final String GROQ_PATH  = "/openai/v1/chat/completions";
  private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

  private static final int  MAX_RETRIES    = 5;
  private static final long RETRY_DELAY_MS = 3000L;

  private final WebClient httpClient;
  private final String    apiKey;
  private final Vertx     vertx;

  public GroqClient(Vertx vertx) {
    this.vertx      = vertx;
    this.httpClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setDefaultHost("api.groq.com")
      .setDefaultPort(443)
      .setConnectTimeout(10000)    // 10s connect timeout
      .setIdleTimeout(30));        // 30s idle timeout

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
          System.out.printf("[GroqClient] 503 received, retrying in %dms (attempt %d/%d)%n",
            RETRY_DELAY_MS, attempt, MAX_RETRIES);
          return vertx.timer(RETRY_DELAY_MS)
            .compose(v -> askWithRetry(prompt, attempt + 1));
        }
        return Future.failedFuture(
          "Groq API error: HTTP " + response.statusCode()
            + " (after " + attempt + " attempt(s)) — "
            + response.bodyAsString()
        );
      })
      // Retry on DNS/connection failures (not just 503)
      .recover(err -> {
        if (attempt < MAX_RETRIES && isNetworkError(err)) {
          System.out.printf("[GroqClient] Network error, retrying in %dms (attempt %d/%d): %s%n",
            RETRY_DELAY_MS, attempt, MAX_RETRIES, err.getMessage());
          return vertx.timer(RETRY_DELAY_MS)
            .compose(v -> askWithRetry(prompt, attempt + 1));
        }
        return Future.failedFuture(err);
      });
  }

  // DNS failures and connection refused are transient — worth retrying
  private boolean isNetworkError(Throwable err) {
    String msg = err.getMessage();
    return msg != null && (
      msg.contains("Failed to resolve") ||
        msg.contains("Connection refused") ||
        msg.contains("Connection reset") ||
        msg.contains("UnknownHostException")
    );
  }

  private JsonObject buildRequestBody(String prompt) {
    return new JsonObject()
      .put("model", GROQ_MODEL)
      .put("messages", new JsonArray()
        .add(new JsonObject()
          .put("role", "user")
          .put("content", prompt)))
      .put("temperature", 0.1);
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
