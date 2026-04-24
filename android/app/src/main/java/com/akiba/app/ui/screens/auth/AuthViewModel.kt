package com.akiba.app.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akiba.app.data.remote.dto.*
import com.akiba.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state — every screen observes one of these ────────────────────────────
sealed class AuthUiState<out T> {
    object Idle                           : AuthUiState<Nothing>()
    object Loading                        : AuthUiState<Nothing>()
    data class Success<T>(val data: T)    : AuthUiState<T>()
    data class Error(val message: String) : AuthUiState<Nothing>()
}

// ── 4-step registration data collected across wizard steps ───────────────────
data class RegisterStep1(
    val fullName        : String = "",
    val email           : String = "",
    val phone           : String = "",
    val password        : String = "",
    val confirmPassword : String = "",
)

data class RegisterStep2(
    val incomeRange    : String = "",
    val employmentType : String = "",
    val primaryGoal    : String = "",
)

data class BudgetSetup(
    val category : String,
    val amount   : Float,
)

data class RegisterStep3(
    val budgets: List<BudgetSetup> = emptyList(),
)

data class RegisterStep4(
    val bankName        : String = "",
    val accountNickname : String = "",
    val accountType     : String = "",
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    // ── Per-operation state flows ─────────────────────────────────────────
    private val _loginState    = MutableStateFlow<AuthUiState<AuthResponse>>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState<AuthResponse>> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<AuthUiState<AuthResponse>>(AuthUiState.Idle)
    val registerState: StateFlow<AuthUiState<AuthResponse>> = _registerState.asStateFlow()

    private val _otpState      = MutableStateFlow<AuthUiState<AuthResponse>>(AuthUiState.Idle)
    val otpState: StateFlow<AuthUiState<AuthResponse>> = _otpState.asStateFlow()

    private val _forgotState   = MutableStateFlow<AuthUiState<MessageResponse>>(AuthUiState.Idle)
    val forgotState: StateFlow<AuthUiState<MessageResponse>> = _forgotState.asStateFlow()

    private val _resetState    = MutableStateFlow<AuthUiState<MessageResponse>>(AuthUiState.Idle)
    val resetState: StateFlow<AuthUiState<MessageResponse>> = _resetState.asStateFlow()

    // ── Registration wizard state — survives recomposition ───────────────
    var step1 by mutableStateOf(RegisterStep1()); private set
    var step2 by mutableStateOf(RegisterStep2()); private set
    var step3 by mutableStateOf(RegisterStep3()); private set
    var step4 by mutableStateOf(RegisterStep4()); private set

    fun updateStep1(update: RegisterStep1.() -> RegisterStep1) { step1 = step1.update() }
    fun updateStep2(update: RegisterStep2.() -> RegisterStep2) { step2 = step2.update() }
    fun updateStep3(update: RegisterStep3.() -> RegisterStep3) { step3 = step3.update() }
    fun updateStep4(update: RegisterStep4.() -> RegisterStep4) { step4 = step4.update() }

    // ── Auth operations ───────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            _loginState.value = repository.login(email, password).fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Login failed") },
            )
        }
    }

    fun register() {
        viewModelScope.launch {
            _registerState.value = AuthUiState.Loading

            val s1 = step1
            val s2 = step2

            val request = RegisterRequest(
                fullName       = s1.fullName,
                email          = s1.email,
                phone          = s1.phone,
                password       = s1.password,
                incomeRange    = s2.incomeRange.ifBlank { null },
                employmentType = s2.employmentType.ifBlank { null },
                primaryGoal    = s2.primaryGoal.ifBlank { null },
            )

            _registerState.value = repository.register(request).fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Registration failed") },
            )
        }
    }

    fun verifyOtp(email: String, otp: String, type: String) {
        viewModelScope.launch {
            _otpState.value = AuthUiState.Loading
            _otpState.value = repository.verifyOtp(email, otp, type).fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "OTP verification failed") },
            )
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _forgotState.value = AuthUiState.Loading
            _forgotState.value = repository.forgotPassword(email).fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Request failed") },
            )
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _resetState.value = AuthUiState.Loading
            _resetState.value = repository.resetPassword(email, otp, newPassword).fold(
                onSuccess = { AuthUiState.Success(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Reset failed") },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _loginState.value    = AuthUiState.Idle
            _registerState.value = AuthUiState.Idle
        }
    }

    // Call these after consuming an error so the screen returns to Idle
    fun resetLoginState()    { _loginState.value    = AuthUiState.Idle }
    fun resetRegisterState() { _registerState.value = AuthUiState.Idle }
    fun resetOtpState()      { _otpState.value      = AuthUiState.Idle }
    fun resetForgotState()   { _forgotState.value   = AuthUiState.Idle }
    fun resetResetState()    { _resetState.value    = AuthUiState.Idle }
}
