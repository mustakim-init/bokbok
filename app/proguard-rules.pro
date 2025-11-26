# General
-dontwarn java.lang.invoke.*
-dontwarn **$$Lambda$*

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlin
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Serialization
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# WebRTC (Library)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# DataStore
-keep class androidx.datastore.** { *; }

# Reflection (keep if using)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 🛑 APP SPECIFIC RULES (Fixes Crash)
# Keep all data models for Firestore/Gson serialization
-keep class com.mustakim.bokbok.data.model.** { *; }

# Keep ViewModels to ensure they can be instantiated
-keep class com.mustakim.bokbok.viewmodel.** { *; }

# Keep Repositories (Safe to keep)
-keep class com.mustakim.bokbok.data.repository.** { *; }

# Keep WebRTC Wrapper & Service (VoiceService is critical)
-keep class com.mustakim.bokbok.data.webrtc.** { *; }

# 🛑 NEW: Keep API Interfaces (Retrofit needs this)
-keep class com.mustakim.bokbok.data.api.** { *; }

# 🛑 NEW: Keep Services & Receivers (Android Components)
-keep class com.mustakim.bokbok.data.service.** { *; }
-keep class com.mustakim.bokbok.data.receiver.** { *; }

# Retrofit & Gson
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn com.google.gson.**

# 🛑 STRIP LOGGING
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
