package com.akiba.parsing.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class GeminiClient {

  private static final String GEMINI_PATH =
    "/v1/models/gemini-2.5-flash:generateContent";

  private static final int    MAX_RETRIES   = 3;
  private static final long   RETRY_DELAY_MS = 2000L; // 2 seconds between retries

  private final WebClient httpClient;
  private final String    apiKey;
  private final Vertx     vertx;

  public GeminiClient(Vertx vertx) {
    this.vertx      = vertx;
    this.httpClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setDefaultHost("generativelanguage.googleapis.com")
      .setDefaultPort(443));

    this.apiKey = System.getenv("GEMINI_API_KEY");
    if (this.apiKey == null || this.apiKey.isBlank()) {
      throw new IllegalStateException("GEMINI_API_KEY env var is not set");
    }
  }

  /**
   * Sends a plain-text prompt to Gemini and returns the text response.
   * Automatically retries up to 3 times on 503 (service unavailable).
   *
   * @param prompt  The full prompt string to send
   * @return        Future resolving to the raw text Gemini returned
   */
  public Future<String> ask(String prompt) {
    return askWithRetry(prompt, 1);
  }

  // Recursive retry — attempt N, if 503 wait 2s then attempt N+1
  private Future<String> askWithRetry(String prompt, int attempt) {
    JsonObject body = buildRequestBody(prompt);

    return httpClient
      .post(GEMINI_PATH + "?key=" + apiKey)
      .sendJsonObject(body)
      .compose(response -> {

        // Success
        if (response.statusCode() == 200) {
          return Future.succeededFuture(extractText(response.bodyAsJsonObject()));
        }

        // Gemini is overloaded — retry if we have attempts left
        if (response.statusCode() == 503 && attempt < MAX_RETRIES) {
          System.out.printf(
            "[GeminiClient] 503 received, retrying in %dms (attempt %d/%d)%n",
            RETRY_DELAY_MS, attempt, MAX_RETRIES
          );
          // Wait RETRY_DELAY_MS then recurse with attempt + 1
          return vertx.timer(RETRY_DELAY_MS)
            .compose(v -> askWithRetry(prompt, attempt + 1));
        }

        // Any other error, or we've exhausted retries
        return Future.failedFuture(
          "Gemini API error: HTTP " + response.statusCode()
            + " (after " + attempt + " attempt(s)) — "
            + response.bodyAsString()
        );
      });
  }

  private JsonObject buildRequestBody(String prompt) {
    return new JsonObject()
      .put("contents", new JsonArray()
        .add(new JsonObject()
          .put("parts", new JsonArray()
            .add(new JsonObject().put("text", prompt)))));
  }

  private String extractText(JsonObject responseBody) {
    try {
      return responseBody
        .getJsonArray("candidates")
        .getJsonObject(0)
        .getJsonObject("content")
        .getJsonArray("parts")
        .getJsonObject(0)
        .getString("text", "");
    } catch (Exception e) {
      throw new RuntimeException("Unexpected Gemini response shape: " + responseBody, e);
    }
  }
}
