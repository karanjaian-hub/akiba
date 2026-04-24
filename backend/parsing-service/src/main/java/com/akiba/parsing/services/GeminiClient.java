package com.akiba.parsing.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Thin wrapper around Gemini 1.5 Flash.
 *
 * Isolated here so every other service calls this one method
 * instead of each building its own HTTP plumbing.
 */
public class GeminiClient {

  private static final String GEMINI_BASE_URL =
    "https://generativelanguage.googleapis.com";

  private static final String GEMINI_PATH =
    "/v1/models/gemini-2.5-flash:generateContent";
  // "/v1/models/gemini-2.5-flash-latest:generateContent";

  private final WebClient httpClient;
  private final String    apiKey;

  public GeminiClient(Vertx vertx) {
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
   *
   * @param prompt  The full prompt string to send
   * @return        Future resolving to the raw text Gemini returned
   */
  public Future<String> ask(String prompt) {
    JsonObject body = buildRequestBody(prompt);

    return httpClient
      .post(GEMINI_PATH + "?key=" + apiKey)
      .sendJsonObject(body)
      .compose(response -> {
        if (response.statusCode() != 200) {
          return Future.failedFuture(
            "Gemini API error: HTTP " + response.statusCode()
              + " — " + response.bodyAsString()
          );
        }
        return Future.succeededFuture(extractText(response.bodyAsJsonObject()));
      });
  }

  // Gemini expects this specific JSON envelope shape
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
