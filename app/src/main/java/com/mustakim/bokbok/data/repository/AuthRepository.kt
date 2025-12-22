package com.mustakim.bokbok.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private val credentialManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CredentialManager.create(context)
        } else null
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun supportsCredentialManager(): Boolean {
        // Force legacy flow for problematic manufacturers (Xiaomi/MIUI)
        val manufacturer = Build.MANUFACTURER.lowercase()
        val problematicManufacturers = listOf("xiaomi", "redmi", "poco")
        
        if (problematicManufacturers.any { manufacturer.contains(it) }) {
            android.util.Log.d("AuthRepository", "Using legacy Google Sign-In for $manufacturer device")
            return false // Force legacy flow for Xiaomi devices
        }
        
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }



    // Modern Google sign-in using Credential Manager
    suspend fun signInWithGoogle(
        activity: Activity
    ): Result<Triple<FirebaseUser, User?, Boolean>> {
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
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")

            // Single Firestore read
            val userDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            val existingUser: User? = if (userDoc.exists()) {
                val data = userDoc.data ?: emptyMap<String, Any>()
                User.fromMap(data)
            } else {
                null
            }
            val isNewUser = existingUser == null

            Result.success(Triple(firebaseUser, existingUser, isNewUser))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // Legacy Google Sign-In (for fallback / older devices)
    suspend fun handleLegacyGoogleSignInResult(
        data: Intent?
    ): Result<Triple<FirebaseUser, User?, Boolean>> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw Exception("No ID token found")

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to sign in with Google")

            val userDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            val existingUser: User? = if (userDoc.exists()) {
                val data = userDoc.data ?: emptyMap<String, Any>()
                User.fromMap(data)
            } else {
                null
            }
            val isNewUser = existingUser == null

            Result.success(Triple(firebaseUser, existingUser, isNewUser))
        } catch (e: ApiException) {
            // Enhanced error logging for API exceptions
            android.util.Log.e("AuthRepository", 
                "Google Sign-In API Error: ${e.statusCode} | " +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} | " +
                "Android: ${Build.VERSION.SDK_INT}")
            Result.failure(Exception("Google Sign-In failed (Error ${e.statusCode}): ${e.message}"))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google Sign-In Error", e)
            Result.failure(e)
        }
    }

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

    // REMOVED: updateUsername method (no longer needed)

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