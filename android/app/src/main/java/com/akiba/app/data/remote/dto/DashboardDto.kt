package com.akiba.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Transactions ──────────────────────────────────────────────────────────────

data class TransactionDto(
    val id          : String,
    val merchant    : String,
    val amount      : Double,
    val type        : String,   // "debit" or "credit"
    val category    : String,
    val source      : String,   // "mpesa", "bank", "manual"
    val date        : String,
    val reference   : String?   = null,
    val rawText     : String?   = null,
    val isAnomalous : Boolean   = false,
)

data class TransactionSummaryDto(
    val totalBalance : Double  = 0.0,
    val totalIncome  : Double? = null,
    val totalExpenses: Double? = null,
    val totalSaved   : Double  = 0.0,
    val month        : String  = "",
    val topCategories: List<String> = emptyList(),
)

data class TopMerchantDto(
    val merchant    : String,
    val totalSpent  : Double,
    val count       : Int,
    val category    : String,
)

// ── Budgets ───────────────────────────────────────────────────────────────────

data class BudgetDto(
    val id        : String  = "",
    val category  : String,
    val limit     : Double  = 0.0,
    val spent     : Double  = 0.0,
    val remaining : Double  = 0.0,
    val percentage: Double  = 0.0,
)

data class BudgetOverviewDto(
    val budgets      : List<BudgetDto>,
    val totalLimit   : Double,
    val totalSpent   : Double,
    val totalRemaining: Double,
)

data class BudgetCheckResult(
    val canAfford       : Boolean,
    val category        : String,
    @SerializedName("current_spent")
    val currentSpent    : Double,
    val limit           : Double,
    @SerializedName("percentage_after")
    val projectedPercent: Double,
    val remaining       : Double,
    @SerializedName("would_exceed")
    val wouldExceed     : Boolean = false,
)

data class GoalsResponse(
    val goals: List<SavingsGoalDto> = emptyList(),
)

// ── Savings ───────────────────────────────────────────────────────────────────

data class SavingsGoalDto(
    val id                    : String,
    val name                  : String,
    @com.google.gson.annotations.SerializedName("icon")
    val emoji                 : String  = "💰",
    @com.google.gson.annotations.SerializedName("targetAmount")
    val targetAmount          : Double,
    @com.google.gson.annotations.SerializedName("currentAmount")
    val savedAmount           : Double  = 0.0,
    val deadline              : String? = null,
    val status                : String  = "ACTIVE",
    @com.google.gson.annotations.SerializedName("percentComplete")
    val percentage            : Double  = 0.0,
    val daysRemaining         : Int?    = null,
    @com.google.gson.annotations.SerializedName("requiredWeeklySaving")
    val weeklyTarget          : Double? = null,
    val isOnTrack             : Boolean = true,
)

data class ContributionDto(
    val id     : String,
    val amount : Double,
    val date   : String,
    val goalId : String,
)
