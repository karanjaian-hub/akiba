package com.akiba.app.ui.screens.dashboard

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akiba.app.data.remote.api.BudgetApiService
import com.akiba.app.data.remote.api.SavingsApiService
import com.akiba.app.data.remote.api.TransactionApiService
import com.akiba.app.data.local.dataStore
import com.akiba.app.data.remote.dto.BudgetOverviewDto
import com.akiba.app.data.remote.dto.SavingsGoalDto
import com.akiba.app.data.remote.dto.TransactionSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val summary       : TransactionSummaryDto? = null,
    val budgetOverview: BudgetOverviewDto?     = null,
    val goals         : List<SavingsGoalDto>   = emptyList(),
    val unreadCount   : Int                    = 0,
    val isLoading     : Boolean                = true,
    val error         : String?                = null,
)

private val BALANCE_HIDDEN_KEY = booleanPreferencesKey("akiba_balance_hidden")

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionApi : TransactionApiService,
    private val budgetApi      : BudgetApiService,
    private val savingsApi     : SavingsApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    // Balance visibility — persisted in DataStore
    val balanceVisible: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> !(prefs[BALANCE_HIDDEN_KEY] ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fire all 3 calls in parallel — don't wait for each sequentially
            val summaryDeferred  = async { runCatching { transactionApi.getSummary() } }
            val budgetDeferred   = async { runCatching { budgetApi.getBudgetOverview() } }
            val goalsDeferred    = async { runCatching { savingsApi.getGoals() } }

            val summary  = summaryDeferred.await().getOrNull()?.body()
            val budget   = budgetDeferred.await().getOrNull()?.body()
            val goals    = goalsDeferred.await().getOrNull()?.body() ?: emptyList()

            _uiState.update {
                it.copy(
                    summary        = summary,
                    budgetOverview = budget,
                    goals          = goals,
                    isLoading      = false,
                    error          = if (summary == null) "Failed to load dashboard" else null,
                )
            }
        }
    }

    fun refresh() = loadDashboard()

    fun toggleBalance() {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[BALANCE_HIDDEN_KEY] ?: false
                prefs[BALANCE_HIDDEN_KEY] = !current
            }
        }
    }
}
