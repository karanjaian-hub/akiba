package com.akiba.parsing.services;

import com.akiba.parsing.models.ParsedTransaction;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-step pipeline:
 *   Step 1 — POST base64 PDF to pdf-helper service → get plain text back
 *   Step 2 — Send that text to Gemini → get structured transactions back
 *
 * We offload PDF extraction to a Python sidecar (pdf-helper) because
 * PyMuPDF is far better at Kenyan bank PDFs than Java PDF libraries.
 */
public class BankPdfParserService {

  private static final String EXTRACT_PROMPT_TEMPLATE = """
        You are a Kenyan bank statement parser. Extract ALL transactions from the text below.
        The text came from a Kenyan bank statement (KCB, Equity, Co-op, NCBA, DTB, etc.).

        Return ONLY a valid JSON array with no markdown, no explanation, no code fences.
        Each object must have these exact fields:
        - date        (string, format: YYYY-MM-DD)
        - amount      (number, in KES, positive value only)
        - type        (string, exactly "DEBIT" or "CREDIT")
        - description (string, transaction description or narration)
        - balance     (number, running balance after this transaction, 0 if not shown)
        - reference   (string, transaction reference or empty string)

        BANK STATEMENT TEXT:
        %s
        """;

  private final GeminiClient geminiClient;
  private final WebClient    httpClient;

  public BankPdfParserService(Vertx vertx, GeminiClient geminiClient) {
    this.geminiClient = geminiClient;
    // pdf-helper is a Docker sidecar — hostname matches docker-compose service name
    this.httpClient   = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost("pdf-helper")
      .setDefaultPort(8099));
  }

  /**
   * @param base64Pdf  Base64-encoded PDF bytes
   * @return           List of extracted transactions
   */
  public Future<List<ParsedTransaction>> parse(String base64Pdf) {
    if (base64Pdf == null || base64Pdf.isBlank()) {
      return Future.succeededFuture(List.of());
    }

    return extractTextFromPdf(base64Pdf)
      .compose(this::parseTextWithGemini);
  }

  // Step 1: Call the Python pdf-helper sidecar to convert PDF bytes → plain text
  private Future<String> extractTextFromPdf(String base64Pdf) {
    JsonObject body = new JsonObject().put("pdf", base64Pdf);

    return httpClient.post("/extract")
      .sendJsonObject(body)
      .compose(response -> {
        if (response.statusCode() != 200) {
          return Future.failedFuture(
            "pdf-helper error: HTTP " + response.statusCode()
              + " — " + response.bodyAsString()
          );
        }
        String extractedText = response.bodyAsJsonObject().getString("text", "");
        if (extractedText.isBlank()) {
          return Future.failedFuture("pdf-helper returned empty text — PDF may be scanned/image-only");
        }
        return Future.succeededFuture(extractedText);
      });
  }

  // Step 2: Ask Gemini to turn that wall of text into structured JSON
  private Future<List<ParsedTransaction>> parseTextWithGemini(String pdfText) {
    String prompt = EXTRACT_PROMPT_TEMPLATE.formatted(pdfText.trim());

    return geminiClient.ask(prompt)
      .compose(this::parseGeminiResponse);
  }

  private Future<List<ParsedTransaction>> parseGeminiResponse(String geminiText) {
    try {
      String cleaned = geminiText.strip()
        .replaceAll("^```json\\s*", "")
        .replaceAll("^```\\s*",     "")
        .replaceAll("```$",         "")
        .strip();

      JsonArray array = new JsonArray(cleaned);
      List<ParsedTransaction> transactions = new ArrayList<>();

      for (int i = 0; i < array.size(); i++) {
        // Bank PDF response uses "description" not "merchant" — fromJson handles both
        transactions.add(ParsedTransaction.fromJson(array.getJsonObject(i)));
      }

      return Future.succeededFuture(transactions);

    } catch (Exception e) {
      return Future.failedFuture(
        "Failed to parse Gemini bank PDF response: " + e.getMessage()
          + " | Raw response: " + geminiText
      );
    }
  }
}
