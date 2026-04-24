package com.akiba.app.data.remote.api

import com.akiba.app.data.remote.dto.BudgetDto
import com.akiba.app.data.remote.dto.BudgetOverviewDto
import com.akiba.app.data.remote.dto.BudgetCheckResult
import retrofit2.Response
import retrofit2.http.*

interface BudgetApiService {

    @GET("budgets")
    suspend fun getBudgets(): Response<List<BudgetDto>>

    @GET("budgets/overview")
    suspend fun getBudgetOverview(): Response<BudgetOverviewDto>

    @GET("budgets/check")
    suspend fun checkBudget(
        @Query("category") category: String,
        @Query("amount")   amount  : Double,
    ): Response<BudgetCheckResult>

    @POST("budgets")
    suspend fun createBudget(@Body request: Map<String, Any>): Response<BudgetDto>
}
