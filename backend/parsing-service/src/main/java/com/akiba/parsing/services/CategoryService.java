package com.akiba.parsing.services;

import com.akiba.parsing.models.ParsedTransaction;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Takes a list of already-parsed transactions and asks Gemini to
 * assign a spending category to each one.
 *
 * We batch all transactions into a single Gemini call (not one per tx)
 * to save on API quota and latency.
 */
public class CategoryService {

  private static final List<String> VALID_CATEGORIES = List.of(
    "Food", "Transport", "Rent", "Bills", "Entertainment",
    "Health", "Savings", "Income", "Other"
  );

  private static final String CATEGORY_PROMPT_TEMPLATE = """
        You are a Kenyan personal finance categorizer.

        For each transaction below, assign the single best category from this list ONLY:
        Food, Transport, Rent, Bills, Entertainment, Health, Savings, Income, Other

        Rules:
        - M-Pesa paybill 888880 = Safaricom bill → Bills
        - Supermarkets, restaurants, food delivery = Food
        - Matatu, Uber, Bolt, fuel = Transport
        - Salary, business income, refunds = Income
        - M-Shwari, savings accounts, investment = Savings
        - If unsure → Other

        Return ONLY a JSON array of objects with fields: reference (string), category (string).
        No markdown, no explanation.

        TRANSACTIONS:
        %s
        """;

  private final GeminiClient geminiClient;

  public CategoryService(GeminiClient geminiClient) {
    this.geminiClient = geminiClient;
  }

  /**
   * @param transactions  List of parsed transactions (category field will be empty)
   * @return              Same list with category field populated on each transaction
   */
  public Future<List<ParsedTransaction>> categorize(List<ParsedTransaction> transactions) {
    if (transactions == null || transactions.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    String txSummary = buildTransactionSummary(transactions);
    String prompt    = CATEGORY_PROMPT_TEMPLATE.formatted(txSummary);

    return geminiClient.ask(prompt)
      .map(geminiText -> applyCategories(transactions, geminiText));
  }

  // Build a compact summary of each tx so the prompt stays small
  private String buildTransactionSummary(List<ParsedTransaction> transactions) {
    return transactions.stream()
      .map(tx -> String.format(
        "{reference: \"%s\", merchant: \"%s\", amount: %.2f, type: \"%s\"}",
        tx.getReference(), tx.getMerchant(), tx.getAmount(), tx.getType().name()
      ))
      .collect(Collectors.joining("\n"));
  }

  private List<ParsedTransaction> applyCategories(
    List<ParsedTransaction> transactions, String geminiText) {

    try {
      String cleaned = geminiText.strip()
        .replaceAll("^```json\\s*", "")
        .replaceAll("^```\\s*",     "")
        .replaceAll("```$",         "")
        .strip();

      JsonArray categoryArray = new JsonArray(cleaned);

      // Build a reference → category lookup map from the AI response
      java.util.Map<String, String> categoryMap = new java.util.HashMap<>();
      for (int i = 0; i < categoryArray.size(); i++) {
        JsonObject item      = categoryArray.getJsonObject(i);
        String reference     = item.getString("reference", "");
        String category      = item.getString("category", "Other");
        // Reject any category the AI invented outside our allowed list
        String safeCategory  = VALID_CATEGORIES.contains(category) ? category : "Other";
        categoryMap.put(reference, safeCategory);
      }

      // Apply categories back to the original transaction objects
      transactions.forEach(tx -> {
        String category = categoryMap.getOrDefault(tx.getReference(), "Other");
        tx.setCategory(category);
      });

    } catch (Exception e) {
      // Non-fatal: if categorization fails, all transactions get "Other"
      System.err.println("[CategoryService] Categorization failed, defaulting to 'Other': " + e.getMessage());
      transactions.forEach(tx -> tx.setCategory("Other"));
    }

    return transactions;
  }
}
