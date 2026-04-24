package com.akiba.app.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akiba.app.data.remote.api.BudgetApiService
import com.akiba.app.data.remote.api.PaymentApiService
import com.akiba.app.data.remote.dto.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI states ─────────────────────────────────────────────────────────────────
sealed class PaymentUiState {
    object Idle                                    : PaymentUiState()
    object Loading                                 : PaymentUiState()
    data class Pending(val paymentId: String)      : PaymentUiState()
    data class Completed(val status: PaymentStatus): PaymentUiState()
    data class Failed(val reason: String)          : PaymentUiState()
    data class Error(val message: String)          : PaymentUiState()
}

sealed class BudgetCheckState {
    object Idle                              : BudgetCheckState()
    object Checking                          : BudgetCheckState()
    data class Result(val warning: BudgetWarning) : BudgetCheckState()
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentApi: PaymentApiService,
    private val budgetApi : BudgetApiService,
) : ViewModel() {

    private val _paymentState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val paymentState: StateFlow<PaymentUiState> = _paymentState.asStateFlow()

    private val _recipients = MutableStateFlow<List<RecipientDto>>(emptyList())
    val recipients: StateFlow<List<RecipientDto>> = _recipients.asStateFlow()

    private val _budgetCheck = MutableStateFlow<BudgetCheckState>(BudgetCheckState.Idle)
    val budgetCheck: StateFlow<BudgetCheckState> = _budgetCheck.asStateFlow()

    // Polling job — kept so we can cancel it on screen exit
    private var pollingJob: Job? = null

    init {
        loadRecipients()
    }

    private fun loadRecipients() {
        viewModelScope.launch {
            runCatching { paymentApi.getRecipients() }
                .getOrNull()?.body()?.let { list ->
                    _recipients.value = list
                }
        }
    }

    fun initiatePayment(request: PaymentRequest) {
        viewModelScope.launch {
            _paymentState.value = PaymentUiState.Loading
            runCatching { paymentApi.initiatePayment(request) }
                .fold(
                    onSuccess = { response ->
                        if (response.isSuccessful) {
                            val body = response.body()!!
                            _paymentState.value = PaymentUiState.Pending(body.paymentId)
                            startPolling(body.paymentId)
                        } else {
                            _paymentState.value = PaymentUiState.Error(
                                "Payment failed: ${response.code()}"
                            )
                        }
                    },
                    onFailure = {
                        _paymentState.value = PaymentUiState.Error(
                            it.message ?: "Payment failed"
                        )
                    },
                )
        }
    }

    // Polls every 3 seconds, times out after 120 seconds
    fun startPolling(paymentId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val timeoutMs = 120_000L
            val intervalMs = 3_000L
            var elapsed = 0L

            while (elapsed < timeoutMs) {
                delay(intervalMs)
                elapsed += intervalMs

                val status = runCatching { paymentApi.getPaymentStatus(paymentId) }
                    .getOrNull()?.body() ?: continue

                when (status.status) {
                    "completed" -> {
                        _paymentState.value = PaymentUiState.Completed(status)
                        return@launch
                    }
                    "failed" -> {
                        _paymentState.value = PaymentUiState.Failed(
                            status.failureReason ?: "Payment failed"
                        )
                        return@launch
                    }
                }
            }
            // Timeout
            _paymentState.value = PaymentUiState.Failed("Payment timed out. Check your M-Pesa.")
        }
    }

    fun checkBudget(category: String, amount: Double) {
        if (category.isBlank() || amount <= 0) return
        viewModelScope.launch {
            _budgetCheck.value = BudgetCheckState.Checking
            runCatching { budgetApi.checkBudget(category, amount) }
                .getOrNull()?.body()?.let { result ->
                    _budgetCheck.value = BudgetCheckState.Result(
                        BudgetWarning(
                            canAfford        = result.canAfford,
                            category         = result.category,
                            projectedPercent = result.projectedPercent,
                            remaining        = result.remaining,
                        )
                    )
                } ?: run {
                    _budgetCheck.value = BudgetCheckState.Idle
                }
        }
    }

    fun resetState() {
        pollingJob?.cancel()
        _paymentState.value = PaymentUiState.Idle
        _budgetCheck.value  = BudgetCheckState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
