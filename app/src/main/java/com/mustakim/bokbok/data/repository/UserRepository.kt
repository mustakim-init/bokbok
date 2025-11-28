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

    // ✅ NEW: Simple in-memory cache
    private data class CachedUser(
        val user: User,
        val timestamp: Long
    )
    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, CachedUser>()
    private val CACHE_TTL = 5 * 60 * 1000L // 5 minutes

    private val usersCollection = firestore.collection("users")

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun getUserProfile(userId: String): Result<User> {
        // 1. Check cache
        val cached = profileCache[userId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL) {
            return Result.success(cached.user)
        }

        // 2. Fetch from network
        return try {
            val doc = usersCollection.document(userId).get().await()
            if (doc.exists()) {
                val user = User.fromMap(doc.data ?: emptyMap())
                // 3. Update cache
                profileCache[userId] = CachedUser(user, System.currentTimeMillis())
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ NEW: Batch fetch method (Optimized)
    suspend fun getUserProfiles(userIds: List<String>): List<User> {
        val uniqueIds = userIds.distinct()
        val resultList = mutableListOf<User>()
        val idsToFetch = mutableListOf<String>()
        val now = System.currentTimeMillis()

        // 1. Get valid cached users
        uniqueIds.forEach { id ->
            val cached = profileCache[id]
            if (cached != null && (now - cached.timestamp) < CACHE_TTL) {
                resultList.add(cached.user)
            } else {
                idsToFetch.add(id)
            }
        }

        // 2. Batch fetch missing users (Firestore limits 'in' queries to 10)
        if (idsToFetch.isNotEmpty()) {
            // Chunk into groups of 10
            idsToFetch.chunked(10).forEach { chunk ->
                try {
                    val snapshot = usersCollection.whereIn("uid", chunk).get().await()
                    snapshot.documents.forEach { doc ->
                        val user = User.fromMap(doc.data ?: emptyMap())
                        profileCache[user.uid] = CachedUser(user, now)
                        resultList.add(user)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return resultList
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            usersCollection.document(userId).update(updates).await()
            // Invalidate cache on update
            profileCache.remove(userId)
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

                // ✅ NEW: Invalidate cache
                profileCache.remove(userId)

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

            // ✅ NEW: Invalidate cache
            profileCache.remove(userId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String, excludeUserId: String? = null): Result<List<User>> {
        return try {
            val snapshot = usersCollection
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThan("username", query + "\uf8ff")
                .limit(20) // ✅ Added limit
                .get()
                .await()

            val users = snapshot.documents.mapNotNull { doc ->
                User.fromMap(doc.data ?: emptyMap())
            }

            // ✅ Filter out excluded user (e.g. self)
            val filtered = if (excludeUserId != null) {
                users.filter { it.uid != excludeUserId }
            } else {
                users
            }

            Result.success(filtered)
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
