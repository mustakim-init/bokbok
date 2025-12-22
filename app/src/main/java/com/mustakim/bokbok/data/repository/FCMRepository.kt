package com.mustakim.bokbok.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton


// Define the API Interface
interface BackendService {
    // Note: The path matches your file name in api/ folder
    @POST("api/send-invite")
    suspend fun sendInvite(@Body body: BackendInviteRequest): BackendResponse
}
data class BackendInviteRequest(
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String>
)
data class BackendResponse(
    val success: Boolean,
    val messageId: String?,
    val error: String?
)


@Singleton
class FCMRepository @Inject constructor(
    private val backendService: BackendService
) {
    suspend fun sendNotification(
        toToken: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = BackendInviteRequest(
                    token = toToken,
                    title = title,
                    body = body,
                    data = data
                )

                val response = backendService.sendInvite(request)

                if (response.success) {
                    android.util.Log.d("FCMRepository", "Successfully sent notification via Vercel")
                    Result.success(Unit)
                } else {
                    android.util.Log.e("FCMRepository", "Backend error: ${response.error}")
                    Result.failure(Exception(response.error))
                }
            } catch (e: Exception) {
                // 👇 NEW: Log the actual error message from the server
                val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                android.util.Log.e("FCMRepository", "Failed to send notification. Server said: $errorBody", e)
                Result.failure(e)
            }
        }
    }
}