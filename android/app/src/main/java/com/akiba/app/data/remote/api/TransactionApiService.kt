package com.akiba.app.data.remote.api

import com.akiba.app.data.remote.dto.TransactionDto
import com.akiba.app.data.remote.dto.TransactionSummaryDto
import com.akiba.app.data.remote.dto.TopMerchantDto
import retrofit2.Response
import retrofit2.http.*

interface TransactionApiService {

    @GET("transactions")
    suspend fun getTransactions(
        @Query("page")     page    : Int    = 0,
        @Query("size")     size    : Int    = 20,
        @Query("category") category: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo")   dateTo  : String? = null,
        @Query("type")     type    : String? = null,
        @Query("source")   source  : String? = null,
    ): Response<List<TransactionDto>>

    @GET("transactions/{id}")
    suspend fun getTransaction(@Path("id") id: String): Response<TransactionDto>

    @GET("transactions/summary")
    suspend fun getSummary(): Response<TransactionSummaryDto>

    @GET("transactions/merchants")
    suspend fun getTopMerchants(): Response<List<TopMerchantDto>>

    @POST("transactions")
    suspend fun createTransaction(@Body request: Map<String, Any>): Response<TransactionDto>

    @PUT("transactions/{id}/category")
    suspend fun updateCategory(
        @Path("id")      id      : String,
        @Query("category") category: String,
    ): Response<TransactionDto>
}
