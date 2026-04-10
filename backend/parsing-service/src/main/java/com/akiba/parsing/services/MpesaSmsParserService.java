package com.akiba.parsing.services;

import com.akiba.parsing.models.ParsedTransaction;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes raw M-Pesa SMS text (one or many messages pasted together)
 * and uses Gemini to extract structured transactions from it.
 *
 * Why Gemini and not a regex? M-Pesa SMS formats change over time and
 * vary by transaction type (send money, pay bill, buy goods, withdraw).
 * A language model handles all variants without needing a regex per type.
 */
public class MpesaSmsParserService {

  private static final String EXTRACT_PROMPT_TEMPLATE = """
        You are a Kenyan M-Pesa SMS parser. Extract ALL transactions from the SMS text below.

        Return ONLY a valid JSON array with no markdown, no explanation, no code fences.
        Each object in the array must have these exact fields:
        - date       (string, format: YYYY-MM-DD, infer year as current if missing)
        - amount     (number, in KES, no commas or currency symbols)
        - type       (string, exactly "DEBIT" or "CREDIT")
        - merchant   (string, the recipient or sender name)
        - reference  (string, M-Pesa transaction ID e.g. "RCK23H...")
        - rawText    (string, the original SMS line this came from)

        SMS TEXT:
        %s
        """;

  private final GeminiClient geminiClient;

  public MpesaSmsParserService(GeminiClient geminiClient) {
    this.geminiClient = geminiClient;
  }

  /**
   * @param rawSmsText  One or more M-Pesa SMS messages pasted as a single string
   * @return            List of extracted transactions (may be empty, never null)
   */
  public Future<List<ParsedTransaction>> parse(String rawSmsText) {
    if (rawSmsText == null || rawSmsText.isBlank()) {
      return Future.succeededFuture(List.of());
    }

    String prompt = EXTRACT_PROMPT_TEMPLATE.formatted(rawSmsText.trim());

    return geminiClient.ask(prompt)
      .compose(this::parseGeminiResponse);
  }

  private Future<List<ParsedTransaction>> parseGeminiResponse(String geminiText) {
    try {
      // Strip any accidental markdown fences Gemini sometimes adds
      String cleaned = geminiText.strip()
        .replaceAll("^```json\\s*", "")
        .replaceAll("^```\\s*",     "")
        .replaceAll("```$",         "")
        .strip();

      JsonArray array = new JsonArray(cleaned);
      List<ParsedTransaction> transactions = new ArrayList<>();

      for (int i = 0; i < array.size(); i++) {
        transactions.add(ParsedTransaction.fromJson(array.getJsonObject(i)));
      }

      return Future.succeededFuture(transactions);

    } catch (Exception e) {
      // Don't crash the whole job for a malformed response; fail with context
      return Future.failedFuture(
        "Failed to parse Gemini M-Pesa response: " + e.getMessage()
          + " | Raw response: " + geminiText
      );
    }
  }
}
