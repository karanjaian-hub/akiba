package com.akiba.ai.services;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * FinancialContextService builds a rich system prompt snapshot by
 * calling the transaction-service and budget-service internally.
 *
 * This runs ONCE per chat request, before we call Gemini, so the AI
 * always has the user's live financial picture. We never pass raw DB
 * data to Gemini — only the summarised context below.
 */
public class FinancialContextService {

  private static final Logger log = LoggerFactory.getLogger(FinancialContextService.class);

  // These resolve inside Docker's akiba-network by service name.
  private static final String TRANSACTION_SERVICE_URL = "http://transaction-service:8082";
  private static final String BUDGET_SERVICE_URL      = "http://budget-service:8086";
  private static final String SAVINGS_SERVICE_URL     = "http://savings-service:8087";

  private final WebClient webClient;

  public FinancialContextService(WebClient webClient) {
    this.webClient = webClient;
  }

  /**
   * Builds the full system prompt that will be injected into every
   * Gemini call for this user. Runs three parallel internal HTTP
   * calls, then formats the result into a concise prompt block.
   */
  public Future<String> buildSystemPrompt(String userId, String authToken) {
    // Fire all three calls in parallel — no point waiting for each one.
    Future<JsonObject> summaryFuture   = fetchTransactionSummary(userId, authToken);
    Future<JsonObject> budgetFuture    = fetchBudgetStatus(userId, authToken);
    Future<JsonObject> savingsFuture   = fetchSavingsGoals(userId, authToken);

    return Future.all(summaryFuture, budgetFuture, savingsFuture)
      .map(cf -> formatSystemPrompt(
        cf.resultAt(0),
        cf.resultAt(1),
        cf.resultAt(2)
      ));
  }

  private Future<JsonObject> fetchTransactionSummary(String userId, String authToken) {
    return webClient
      .getAbs(TRANSACTION_SERVICE_URL + "/internal/summary/" + userId)
      .putHeader("Authorization", "Bearer " + authToken)
      .send()
      .map(r -> r.statusCode() == 200 ? r.bodyAsJsonObject() : new JsonObject())
      .recover(err -> {
        log.warn("Could not fetch transaction summary for user {}: {}", userId, err.getMessage());
        return Future.succeededFuture(new JsonObject());
      });
  }

  private Future<JsonObject> fetchBudgetStatus(String userId, String authToken) {
    return webClient
      .getAbs(BUDGET_SERVICE_URL + "/internal/status/" + userId)
      .putHeader("Authorization", "Bearer " + authToken)
      .send()
      .map(r -> r.statusCode() == 200 ? r.bodyAsJsonObject() : new JsonObject())
      .recover(err -> {
        log.warn("Could not fetch budget status for user {}: {}", userId, err.getMessage());
        return Future.succeededFuture(new JsonObject());
      });
  }

  private Future<JsonObject> fetchSavingsGoals(String userId, String authToken) {
    return webClient
      .getAbs(SAVINGS_SERVICE_URL + "/internal/goals/" + userId)
      .putHeader("Authorization", "Bearer " + authToken)
      .send()
      .map(r -> r.statusCode() == 200 ? r.bodyAsJsonObject() : new JsonObject())
      .recover(err -> {
        log.warn("Could not fetch savings goals for user {}: {}", userId, err.getMessage());
        return Future.succeededFuture(new JsonObject());
      });
  }

  /**
   * Formats the three data blobs into a concise prompt block.
   *
   * We keep it readable plain-text (not JSON) because LLMs reason
   * better over natural language than raw structured data.
   */
  private String formatSystemPrompt(JsonObject summary, JsonObject budget, JsonObject savings) {
    LocalDate today = LocalDate.now();
    String monthName = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

    double income        = summary.getDouble("totalIncome", 0.0);
    double totalExpenses = summary.getDouble("totalExpenses", 0.0);
    JsonArray topCats    = summary.getJsonArray("topCategories", new JsonArray());
    JsonArray anomalies  = summary.getJsonArray("anomalousTransactions", new JsonArray());

    JsonArray overBudget = budget.getJsonArray("overLimitCategories", new JsonArray());

    JsonArray goals      = savings.getJsonArray("activeGoals", new JsonArray());

    StringBuilder sb = new StringBuilder();
    sb.append("You are Akiba, a friendly and insightful personal finance assistant for a Kenyan user. ");
    sb.append("Your job is to help them understand their spending, stay within budgets, and reach savings goals. ");
    sb.append("Always speak in clear, encouraging language. Use Kenyan Shillings (Ksh) for all amounts.\n\n");

    sb.append("=== USER'S FINANCIAL SNAPSHOT (").append(monthName).append(" ").append(today.getYear()).append(") ===\n");
    sb.append("Today's date: ").append(today).append("\n\n");

    sb.append("INCOME & EXPENSES THIS MONTH:\n");
    sb.append("  Total Income:   Ksh ").append(String.format("%.2f", income)).append("\n");
    sb.append("  Total Expenses: Ksh ").append(String.format("%.2f", totalExpenses)).append("\n");
    sb.append("  Net:            Ksh ").append(String.format("%.2f", income - totalExpenses)).append("\n\n");

    sb.append("TOP 3 SPENDING CATEGORIES:\n");
    for (int i = 0; i < Math.min(3, topCats.size()); i++) {
      JsonObject cat = topCats.getJsonObject(i);
      sb.append("  ").append(i + 1).append(". ")
        .append(cat.getString("name", "Unknown"))
        .append(" — Ksh ").append(String.format("%.2f", cat.getDouble("amount", 0.0)))
        .append("\n");
    }

    sb.append("\nBUDGET STATUS:\n");
    if (overBudget.isEmpty()) {
      sb.append("  ✅ All categories within budget.\n");
    } else {
      sb.append("  ⚠️ Over-budget categories:\n");
      for (Object item : overBudget) {
        JsonObject cat = (JsonObject) item;
        sb.append("     - ").append(cat.getString("name"))
          .append(": spent Ksh ").append(String.format("%.2f", cat.getDouble("spent", 0.0)))
          .append(" / limit Ksh ").append(String.format("%.2f", cat.getDouble("limit", 0.0)))
          .append("\n");
      }
    }

    sb.append("\nACTIVE SAVINGS GOALS:\n");
    if (goals.isEmpty()) {
      sb.append("  No active savings goals.\n");
    } else {
      for (Object item : goals) {
        JsonObject goal = (JsonObject) item;
        double saved  = goal.getDouble("savedAmount", 0.0);
        double target = goal.getDouble("targetAmount", 1.0);
        int pct = (int) ((saved / target) * 100);
        sb.append("  - ").append(goal.getString("name"))
          .append(": Ksh ").append(String.format("%.2f", saved))
          .append(" / Ksh ").append(String.format("%.2f", target))
          .append(" (").append(pct).append("%)\n");
      }
    }

    if (!anomalies.isEmpty()) {
      sb.append("\nANOMALOUS TRANSACTIONS THIS MONTH:\n");
      for (Object item : anomalies) {
        JsonObject tx = (JsonObject) item;
        sb.append("  - ").append(tx.getString("description", "Unknown"))
          .append(" — Ksh ").append(String.format("%.2f", tx.getDouble("amount", 0.0)))
          .append("\n");
      }
    }

    sb.append("\n===================================================\n");
    sb.append("Use the above data to answer the user's question accurately and helpfully.");

    return sb.toString();
  }
}
