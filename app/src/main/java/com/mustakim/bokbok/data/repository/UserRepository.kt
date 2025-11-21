package com.mustakim.bokbok.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mustakim.bokbok.BuildConfig
import com.mustakim.bokbok.data.api.ImgBBApi
import com.mustakim.bokbok.data.model.User
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream


class UserRepository(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val imgBBApi = ImgBBApi.create()

    private val usersCollection = firestore.collection("users")

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun getUserProfile(userId: String): Result<User> {
        return try {
            val doc = usersCollection.document(userId).get().await()
            if (doc.exists()) {
                val user = User.fromMap(doc.data ?: emptyMap())
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            usersCollection.document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            // Read and compress image
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                return Result.failure(Exception("Failed to load image"))
            }

            // Compress
            val outputStream = ByteArrayOutputStream()
            var quality = 90
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            while (outputStream.size() > 1024 * 1024 && quality > 20) {
                outputStream.reset()
                quality -= 10
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }

            val bytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            // Upload using Retrofit
            val response = imgBBApi.uploadImage(BuildConfig.IMGBB_API_KEY, base64Image)

            if (response.success && response.data != null) {
                val imageUrl = response.data.url

                // Update Firestore
                usersCollection.document(userId)
                    .update("profileImageUrl", imageUrl)
                    .await()

                Result.success(imageUrl)
            } else {
                Result.failure(Exception("Failed to upload image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun deleteProfileImage(userId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("profileImageUrl", "")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            // Simple prefix search on username
            val snapshot = usersCollection
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThan("username", query + "\uf8ff")
                .get()
                .await()
            val users = snapshot.documents.mapNotNull { doc ->
                User.fromMap(doc.data ?: emptyMap())
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(token: String) {
        val userId = getCurrentUserId() ?: return
        try {
            usersCollection.document(userId)
                .update("fcmToken", token)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
