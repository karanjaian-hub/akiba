package com.akiba.savings.models;

import io.vertx.core.json.JsonObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Domain model for a savings goal.
 *
 * All the "smart" derived fields (percentComplete, isOnTrack, etc.) are computed
 * here in one place rather than scattered across handlers and repositories.
 * This way the HTTP response is always consistent, whether data comes from DB or Redis cache.
 */
public class SavingsGoal {

  public String  id;
  public String  userId;
  public String  name;
  public double  targetAmount;
  public double  currentAmount;
  public String  deadline;      // ISO date: "2025-12-31"
  public String  icon;
  public String  status;
  public String  createdAt;

  // ── Computed fields (derived on every read) ────────────────────────────

  public double percentComplete;
  public long   daysRemaining;
  public double requiredWeeklySaving;
  public boolean isOnTrack;
  public String  projectedCompletionDate;

  public static SavingsGoal fromJson(JsonObject json) {
    SavingsGoal goal = new SavingsGoal();
    goal.id            = json.getString("id");
    goal.userId        = json.getString("userId");
    goal.name          = json.getString("name");
    goal.targetAmount  = json.getDouble("targetAmount", 0.0);
    goal.currentAmount = json.getDouble("currentAmount", 0.0);
    goal.deadline      = json.getString("deadline");
    goal.icon          = json.getString("icon");
    goal.status        = json.getString("status", "ACTIVE");
    goal.createdAt     = json.getString("createdAt");
    goal.computeDerivedFields();
    return goal;
  }

  /**
   * All math for progress tracking lives here.
   *
   * Linear projection: if the goal started on createdAt and ends on deadline,
   * by today the user "should" have saved proportionally. We compare that
   * expected amount against currentAmount to determine on-track status.
   */
  public void computeDerivedFields() {
    percentComplete = targetAmount > 0
      ? Math.min(100.0, (currentAmount / targetAmount) * 100)
      : 0;

    LocalDate deadlineDate = LocalDate.parse(deadline);
    LocalDate today        = LocalDate.now();
    daysRemaining = Math.max(0, ChronoUnit.DAYS.between(today, deadlineDate));

    double weeksRemaining = daysRemaining / 7.0;
    double amountLeft     = Math.max(0, targetAmount - currentAmount);
    requiredWeeklySaving  = weeksRemaining > 0 ? amountLeft / weeksRemaining : amountLeft;

    // On-track: compare actual vs linear expectation
    if (createdAt != null) {
      LocalDate startDate    = LocalDate.parse(createdAt.substring(0, 10));
      long totalDays         = ChronoUnit.DAYS.between(startDate, deadlineDate);
      long daysPassed        = ChronoUnit.DAYS.between(startDate, today);
      double expectedByNow   = totalDays > 0
        ? (targetAmount * daysPassed) / totalDays
        : 0;
      isOnTrack = currentAmount >= expectedByNow;
    } else {
      isOnTrack = true;
    }

    // Projected completion: at current pace, when would they hit 100%?
    if (currentAmount >= targetAmount) {
      projectedCompletionDate = today.toString();
    } else if (currentAmount <= 0 || daysRemaining <= 0) {
      projectedCompletionDate = null;
    } else {
      // Daily save rate × days needed to cover remaining amount
      double dailyRate = currentAmount / Math.max(1, ChronoUnit.DAYS.between(
        LocalDate.parse(createdAt != null ? createdAt.substring(0, 10) : today.toString()), today));
      long daysToComplete = dailyRate > 0
        ? (long) Math.ceil(amountLeft / dailyRate)
        : Long.MAX_VALUE;
      projectedCompletionDate = daysToComplete < 3650
        ? today.plusDays(daysToComplete).toString()
        : null;
    }
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("id",                    id)
      .put("userId",                userId)
      .put("name",                  name)
      .put("targetAmount",          targetAmount)
      .put("currentAmount",         currentAmount)
      .put("deadline",              deadline)
      .put("icon",                  icon)
      .put("status",                status)
      .put("createdAt",             createdAt)
      .put("percentComplete",       Math.round(percentComplete * 10.0) / 10.0)
      .put("daysRemaining",         daysRemaining)
      .put("requiredWeeklySaving",  Math.round(requiredWeeklySaving))
      .put("isOnTrack",             isOnTrack)
      .put("projectedCompletionDate", projectedCompletionDate);
  }
}
