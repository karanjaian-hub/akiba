package com.akiba.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Auth requests ─────────────────────────────────────────────────────────────

data class LoginRequest(
    val email    : String,
    val password : String,
)

data class RegisterRequest(
    @SerializedName("full_name")       val fullName      : String,
    val email                          : String,
    val phone                          : String,
    val password                       : String,
    @SerializedName("income_range")    val incomeRange   : String? = null,
    @SerializedName("employment_type") val employmentType: String? = null,
    @SerializedName("primary_goal")    val primaryGoal   : String? = null,
)

data class OtpRequest(
    val email : String,
    val otp   : String,
    val type  : String, // "verify" or "reset"
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
    @SerializedName("access_token")  val accessToken : String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user                         : UserDto,
)

data class UserDto(
    val id                             : String,
    @SerializedName("full_name")       val fullName      : String,
    val email                          : String,
    val phone                          : String,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    @SerializedName("is_verified")     val isVerified    : Boolean = false,
)

data class MessageResponse(
    val message: String,
)
