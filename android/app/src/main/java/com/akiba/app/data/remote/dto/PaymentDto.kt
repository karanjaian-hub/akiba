package com.akiba.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Payment request ───────────────────────────────────────────────────────────
data class PaymentRequest(
    @SerializedName("recipient_type") val recipientType  : String, // "phone"|"till"|"paybill"
    val phone                         : String?           = null,
    val till                          : String?           = null,
    val paybill                       : String?           = null,
    @SerializedName("account_number") val accountNumber  : String? = null,
    val amount                        : Double,
    val category                      : String,
    val pin                           : String,
)

// ── Payment responses ─────────────────────────────────────────────────────────
data class PaymentInitResponse(
    @SerializedName("payment_id") val paymentId : String,
    val status                   : String,        // "pending"
    val message                  : String,
)

data class PaymentStatus(
    @SerializedName("payment_id") val paymentId  : String,
    val status                   : String,        // "pending"|"completed"|"failed"
    val amount                   : Double,
    val recipient                : String,
    val category                 : String,
    val timestamp                : String,
    val reference                : String?        = null,
    @SerializedName("failure_reason") val failureReason: String? = null,
)

// ── Recipient ─────────────────────────────────────────────────────────────────
data class RecipientDto(
    val id           : String,
    val nickname     : String,
    val phone        : String?,
    val till         : String?,
    val paybill      : String?,
    val category     : String,
    @SerializedName("last_used") val lastUsed: String?,
)

// ── Budget check ──────────────────────────────────────────────────────────────
data class BudgetWarning(
    val canAfford       : Boolean,
    val category        : String,
    val projectedPercent: Double,
    val remaining       : Double,
)
