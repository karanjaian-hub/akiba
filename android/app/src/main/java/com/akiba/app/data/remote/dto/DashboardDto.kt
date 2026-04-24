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
    val totalBalance : Double,
    val totalIncome  : Double,
    val totalExpenses: Double,
    val totalSaved   : Double,
    val month        : String,
)

data class TopMerchantDto(
    val merchant    : String,
    val totalSpent  : Double,
    val count       : Int,
    val category    : String,
)

// ── Budgets ───────────────────────────────────────────────────────────────────

data class BudgetDto(
    val id         : String,
    val category   : String,
    val limit      : Double,
    val spent      : Double,
    val remaining  : Double,
    val percentage : Double,
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
    val currentSpent    : Double,
    val limit           : Double,
    @SerializedName("projected_percentage")
    val projectedPercent: Double,
    val remaining       : Double,
)

// ── Savings ───────────────────────────────────────────────────────────────────

data class SavingsGoalDto(
    val id           : String,
    val name         : String,
    val emoji        : String,
    val targetAmount : Double,
    val savedAmount  : Double,
    val deadline     : String?,
    val status       : String,  // "on_track", "behind", "completed"
    val percentage   : Double,
    val daysRemaining: Int?,
    val weeklyTarget : Double?,
)

data class ContributionDto(
    val id     : String,
    val amount : Double,
    val date   : String,
    val goalId : String,
)
