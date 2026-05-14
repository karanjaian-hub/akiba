package com.akiba.app.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class ParseMpesaRequest(
    @SerializedName("sms_text") val smsText: String,
)

data class ParseBankRequest(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("bank_name") val bankName: String,
)

data class ParseResult(
    val parsed     : Int,
    val failed     : Int,
    val transactions: List<String>,
)

interface ParseApiService {

    @POST("parse/mpesa")
    suspend fun parseMpesa(@Body request: ParseMpesaRequest): Response<ParseResult>

    @POST("parse/bank")
    suspend fun parseBank(@Body request: ParseBankRequest): Response<ParseResult>
}
