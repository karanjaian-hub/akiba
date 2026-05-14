package com.akiba.app.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class AiChatRequest(
    val message      : String,
    @SerializedName("conversation_id")
    val conversationId: String? = null,
)

data class AiChatResponse(
    val response     : String,
    @SerializedName("conversation_id")
    val conversationId: String,
    val timestamp    : String? = null,
)

data class AiConversation(
    val id        : String,
    val title     : String,
    val createdAt : String,
    val updatedAt : String,
)

data class AiReport(
    val month    : Int,
    val year     : Int,
    val summary  : String,
    val insights : List<String>,
    val tips     : List<String>,
)

data class AiInsight(
    val id       : String,
    val type     : String,
    val message  : String,
    val category : String? = null,
    val createdAt: String,
)

interface AiApiService {

    @POST("ai/chat")
    suspend fun chat(@Body request: AiChatRequest): Response<AiChatResponse>

    @GET("ai/conversations")
    suspend fun getConversations(): Response<List<AiConversation>>

    @GET("ai/reports/{month}/{year}")
    suspend fun getReport(
        @Path("month") month: Int,
        @Path("year")  year : Int,
    ): Response<AiReport>

    @POST("ai/insights")
    suspend fun generateInsights(): Response<List<AiInsight>>
}
