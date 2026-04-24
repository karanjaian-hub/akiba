package com.akiba.app.data.remote.api

import com.akiba.app.data.remote.dto.PaymentInitResponse
import com.akiba.app.data.remote.dto.PaymentStatus
import com.akiba.app.data.remote.dto.PaymentRequest
import com.akiba.app.data.remote.dto.RecipientDto
import retrofit2.Response
import retrofit2.http.*

interface PaymentApiService {

    @POST("payments/initiate")
    suspend fun initiatePayment(@Body request: PaymentRequest): Response<PaymentInitResponse>

    @GET("payments/{paymentId}/status")
    suspend fun getPaymentStatus(@Path("paymentId") paymentId: String): Response<PaymentStatus>

    @GET("payments/history")
    suspend fun getPaymentHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<List<PaymentStatus>>

    @GET("payments/recipients")
    suspend fun getRecipients(): Response<List<RecipientDto>>

    @POST("payments/recipients")
    suspend fun saveRecipient(@Body request: Map<String, Any>): Response<RecipientDto>

    @PUT("payments/recipients/{id}")
    suspend fun updateRecipient(
        @Path("id") id     : String,
        @Body       request: Map<String, Any>,
    ): Response<RecipientDto>
}
