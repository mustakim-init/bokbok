package com.mustakim.bokbok.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FCMV1Service {
    @POST("v1/projects/{projectId}/messages:send")
    suspend fun sendMessage(
        @Path("projectId") projectId: String,
        @Header("Authorization") authorization: String, // "Bearer <token>"
        @Body message: FCMMessageWrapper
    )
}

data class FCMMessageWrapper(
    val message: FCMMessage
)

data class FCMMessage(
    val token: String,
    val notification: FCMNotification? = null,
    val data: Map<String, String>? = null,
    val android: FCMAndroidConfig? = null
)

data class FCMNotification(
    val title: String,
    val body: String
)

data class FCMAndroidConfig(
    val priority: String = "HIGH"
)
