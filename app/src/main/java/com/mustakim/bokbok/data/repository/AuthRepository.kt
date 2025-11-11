package com.mustakim.bokbok.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(context)

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // Modern Google Sign-In with Credential Manager
    suspend fun signInWithGoogle(): Result<Pair<FirebaseUser, Boolean>> {
        return try {
            val webClientId = context.getString(R.string.default_web_client_id)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = credential.idToken

            // Sign in to Firebase
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")

            // Check if user document exists in Firestore
            val userDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            val isNewUser = !userDoc.exists()

            if (isNewUser) {
                // Create user profile with Google data
                val user = User(
                    uid = firebaseUser.uid,
                    username = "", // Will be set later
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    bio = "",
                    profileImageUrl = firebaseUser.photoUrl?.toString() ?: "",
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    createdAt = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis()
                )

                // Save to Firestore (without username for now)
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(user.toMap())
                    .await()
            }

            Result.success(firebaseUser to isNewUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Check if username is available
    suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return try {
            val result = firestore.collection("users")
                .whereEqualTo("username", username.lowercase())
                .get()
                .await()

            Result.success(result.isEmpty)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Update username for new Google users
    suspend fun updateUsername(userId: String, username: String): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .update("username", username.lowercase())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Email/Password Sign Up (existing)
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Failed to create user")

            // Create user profile in Firestore
            val user = User(
                uid = firebaseUser.uid,
                username = username.lowercase(),
                email = email,
                displayName = displayName,
                bio = "",
                profileImageUrl = "",
                phoneNumber = "",
                createdAt = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(user.toMap())
                .await()

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Email/Password Sign In (existing)
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Failed to sign in")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
