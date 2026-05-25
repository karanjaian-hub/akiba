package com.akiba.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Auth requests ─────────────────────────────────────────────────────────────

data class LoginRequest(
    val email    : String,
    val password : String,
)

data class RegisterRequest(
    val fullName: String,
    val email   : String,
    val phone   : String,
    val password: String,
)

data class OtpRequest(
    val email : String,
    val otp   : String,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class ForgotPasswordRequest(
    val email: String,
)

data class ResetPasswordRequest(
    val email    : String,
    val otp      : String,
    @SerializedName("new_password") val newPassword: String,
)

data class UpdateProfileRequest(
    @SerializedName("full_name") val fullName: String? = null,
    val phone                   : String?     = null,
)

// ── Auth responses ────────────────────────────────────────────────────────────

data class AuthResponse(
    val accessToken : String,
    val refreshToken: String,
    val expiresIn   : Int    = 900,
    val user        : UserDto,
)

data class UserDto(
    val id          : String,
    val fullName    : String,
    val email       : String,
    val role        : String  = "ROLE_USER",
    val phone       : String? = null,
    val profilePicture: String? = null,
)

data class MessageResponse(
    val message: String,
)
