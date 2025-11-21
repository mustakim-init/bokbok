package com.mustakim.bokbok.data.repository

import android.util.Base64
import com.mustakim.bokbok.data.api.FCMAndroidConfig
import com.mustakim.bokbok.data.api.FCMMessage
import com.mustakim.bokbok.data.api.FCMMessageWrapper
import com.mustakim.bokbok.data.api.FCMNotification
import com.mustakim.bokbok.data.api.FCMV1Service
import com.mustakim.bokbok.data.api.GoogleAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class FCMRepository {

    private val fcmService: FCMV1Service
    private val googleAuthService: GoogleAuthService

    init {
        // FCM V1 API
        val fcmRetrofit = Retrofit.Builder()
            .baseUrl("https://fcm.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        fcmService = fcmRetrofit.create(FCMV1Service::class.java)

        // Google OAuth API
        val authRetrofit = Retrofit.Builder()
            .baseUrl("https://oauth2.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        googleAuthService = authRetrofit.create(GoogleAuthService::class.java)
    }

    suspend fun sendNotification(
        serviceAccountJson: String,
        toToken: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Parse Service Account JSON
                val jsonObject = JSONObject(serviceAccountJson)
                val projectId = jsonObject.getString("project_id")
                val clientEmail = jsonObject.getString("client_email")
                val privateKeyPem = jsonObject.getString("private_key")

                // 2. Generate JWT
                val jwt = generateJWT(clientEmail, privateKeyPem)

                // 3. Exchange JWT for Access Token
                val tokenResponse = googleAuthService.getAccessToken(jwt = jwt)
                val accessToken = "Bearer ${tokenResponse.access_token}"

                // 4. Send Notification (Data-only for reliable background handling)
                val dataPayload = data.toMutableMap().apply {
                    put("title", title)
                    put("body", body)
                }

                val message = FCMMessageWrapper(
                    message = FCMMessage(
                        token = toToken,
                        notification = null, // Set to null to force data-only message
                        data = dataPayload,
                        android = FCMAndroidConfig(priority = "HIGH")
                    )
                )
                fcmService.sendMessage(projectId, accessToken, message)
                
                android.util.Log.d("FCMRepository", "Successfully sent notification to token: ${toToken.take(10)}...")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("FCMRepository", "Failed to send FCM notification", e)
                Result.failure(e)
            }
        }
    }

    private fun generateJWT(clientEmail: String, privateKeyPem: String): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600 // 1 hour expiration

        // Header
        val header = JSONObject()
        header.put("alg", "RS256")
        header.put("typ", "JWT")
        val headerBase64 = Base64.encodeToString(header.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        // Payload
        val payload = JSONObject()
        payload.put("iss", clientEmail)
        payload.put("scope", "https://www.googleapis.com/auth/firebase.messaging")
        payload.put("aud", "https://oauth2.googleapis.com/token")
        payload.put("exp", exp)
        payload.put("iat", now)
        val payloadBase64 = Base64.encodeToString(payload.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        // Signature
        val dataToSign = "$headerBase64.$payloadBase64"
        val signatureBytes = sign(dataToSign, privateKeyPem)
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        return "$headerBase64.$payloadBase64.$signatureBase64"
    }

    private fun sign(data: String, privateKeyPem: String): ByteArray {
        // Clean up the PEM string
        val privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "") // Handle escaped newlines from JSON string
            .replace("\n", "")  // Handle actual newlines
            .trim()

        val keyBytes = Base64.decode(privateKeyContent, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray())
        return signature.sign()
    }
}
