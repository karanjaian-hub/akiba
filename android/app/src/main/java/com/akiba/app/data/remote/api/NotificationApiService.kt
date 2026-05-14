package com.akiba.app.data.remote.api

import retrofit2.Response
import retrofit2.http.*

data class UnreadCountResponse(val count: Int)
data class NotificationResponse(
    val id     : String,
    val type   : String,
    val title  : String,
    val message: String,
    val read   : Boolean,
    val createdAt: String,
)

interface NotificationApiService {

    @GET("notifications")
    suspend fun getNotifications(): Response<List<NotificationResponse>>

    @PUT("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<Unit>

    @PUT("notifications/read-all")
    suspend fun markAllRead(): Response<Unit>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>
}
