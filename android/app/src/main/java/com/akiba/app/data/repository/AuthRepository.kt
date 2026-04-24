package com.akiba.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.akiba.app.data.local.PrefKeys
import com.akiba.app.data.remote.api.AuthApiService
import com.akiba.app.data.local.dataStore
import com.akiba.app.data.remote.dto.*
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api    : AuthApiService,
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    // ── Auth operations — all return Result<T> so callers never touch exceptions
    
    suspend fun login(email: String, password: String): Result<AuthResponse> =
        runCatching {
            val response = api.login(LoginRequest(email, password))
            val body     = response.bodyOrThrow()
            saveAuthData(body)
            body
        }

    suspend fun register(request: RegisterRequest): Result<AuthResponse> =
        runCatching {
            val response = api.register(request)
            val body     = response.bodyOrThrow()
            saveAuthData(body)
            body
        }

    suspend fun verifyOtp(email: String, otp: String, type: String): Result<AuthResponse> =
        runCatching {
            val response = api.verifyEmailOtp(OtpRequest(email, otp, type))
            val body     = response.bodyOrThrow()
            saveAuthData(body)
            body
        }

    suspend fun forgotPassword(email: String): Result<MessageResponse> =
        runCatching {
            api.forgotPassword(ForgotPasswordRequest(email)).bodyOrThrow()
        }

    suspend fun resetPassword(
        email      : String,
        otp        : String,
        newPassword: String,
    ): Result<MessageResponse> =
        runCatching {
            api.resetPassword(ResetPasswordRequest(email, otp, newPassword)).bodyOrThrow()
        }

    suspend fun getProfile(): Result<UserDto> =
        runCatching { api.getProfile().bodyOrThrow() }

    suspend fun logout(): Result<Unit> =
        runCatching {
            runCatching { api.logout() } // best-effort — clear local data regardless
            clearAuthData()
        }

    // ── Token helpers ─────────────────────────────────────────────────────────

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[PrefKeys.ACCESS_TOKEN]

    suspend fun isLoggedIn(): Boolean =
        getAccessToken() != null

    // ── DataStore read/write ──────────────────────────────────────────────────

    private suspend fun saveAuthData(auth: AuthResponse) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.ACCESS_TOKEN]  = auth.accessToken
            prefs[PrefKeys.REFRESH_TOKEN] = auth.refreshToken
            prefs[PrefKeys.USER_JSON]     = gson.toJson(auth.user)
        }
    }

    private suspend fun clearAuthData() {
        context.dataStore.edit { it.clear() }
    }

    // ── Extension: throw a readable error if the response body is null ────────
    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T =
        if (isSuccessful) body() ?: error("Empty response body")
        else error("API error ${code()}: ${errorBody()?.string()}")
}
