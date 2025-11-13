package com.mustakim.bokbok.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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

    // Modern Credential Manager (Android 9+)
    private val credentialManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CredentialManager.create(context)
        } else null
    }

    // Legacy Google Sign-In (for older Android)
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    // Check if device supports modern Credential Manager
    fun supportsCredentialManager(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    // Get Google Sign-In Intent (for older devices)
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    // Check if user exists in Firestore by UID (correct way)
    suspend fun checkIfUserExists(uid: String): Result<Boolean> {
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Modern Google Sign-In that returns user status (new vs existing)
    suspend fun signInWithGoogle(activity: Activity): Result<Pair<FirebaseUser, Boolean>> {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return Result.failure(Exception("Use legacy sign-in for older devices"))
            }

            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager!!.getCredential(
                request = request,
                context = activity
            )

            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = credential.idToken

            // Sign in to Firebase
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")

            // Check if user exists in Firestore by UID
            val userExists = checkIfUserExists(firebaseUser.uid).getOrThrow()

            Result.success(firebaseUser to !userExists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Handle legacy Google Sign-In result
    suspend fun handleLegacyGoogleSignInResult(data: Intent?): Result<Pair<FirebaseUser, Boolean>> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            val idToken = account.idToken ?: throw Exception("No ID token found")
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")

            // Check if user exists in Firestore by UID
            val userExists = checkIfUserExists(firebaseUser.uid).getOrThrow()

            Result.success(firebaseUser to !userExists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Create user profile for new Google users
    suspend fun createGoogleUserProfile(user: FirebaseUser, username: String): Result<Unit> {
        return try {
            val userData = User(
                uid = user.uid,
                username = username.lowercase(),
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                bio = "",
                profileImageUrl = user.photoUrl?.toString() ?: "",
                phoneNumber = user.phoneNumber ?: "",
                createdAt = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(user.uid)
                .set(userData.toMap())
                .await()

            Result.success(Unit)
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

    // Update username for existing users (kept for backward compatibility)
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

    // Email/Password Sign Up
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Failed to create user")

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

    // Email/Password Sign In
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
        googleSignInClient.signOut()
    }
}