package com.akiba.app.data.remote.api

import com.akiba.app.data.remote.dto.SavingsGoalDto
import com.akiba.app.data.remote.dto.ContributionDto
import retrofit2.Response
import retrofit2.http.*

interface SavingsApiService {

    @GET("savings/goals")
    suspend fun getGoals(): Response<List<SavingsGoalDto>>

    @POST("savings/goals")
    suspend fun createGoal(@Body request: Map<String, Any>): Response<SavingsGoalDto>

    @PUT("savings/goals/{id}")
    suspend fun updateGoal(
        @Path("id")   id     : String,
        @Body         request: Map<String, Any>,
    ): Response<SavingsGoalDto>

    @DELETE("savings/goals/{id}")
    suspend fun deleteGoal(@Path("id") id: String): Response<Unit>

    @POST("savings/goals/{id}/contribute")
    suspend fun contribute(
        @Path("id")  goalId : String,
        @Body        request: Map<String, Any>,
    ): Response<SavingsGoalDto>

    @GET("savings/goals/{id}/history")
    suspend fun getGoalHistory(@Path("id") id: String): Response<List<ContributionDto>>
}
