import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

android {
    namespace = "com.mustakim.bokbok"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mustakim.bokbok"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Read keys from local.properties
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        // ImgBB
        val imgbbKey = properties.getProperty("imgbb.api.key") ?: ""
        buildConfigField("String", "IMGBB_API_KEY", "\"$imgbbKey\"")

        // TURN
        val turnUrl = properties.getProperty("TURN_URL") ?: ""
        val turnUser = properties.getProperty("TURN_USERNAME") ?: ""
        val turnPass = properties.getProperty("TURN_PASSWORD") ?: ""

        buildConfigField("String", "TURN_URL", "\"$turnUrl\"")
        buildConfigField("String", "TURN_USERNAME", "\"$turnUser\"")
        buildConfigField("String", "TURN_PASSWORD", "\"$turnPass\"")

        val fallbackUrl = properties.getProperty("TURN_FALLBACK_URL") ?: ""
        val fallbackUser = properties.getProperty("TURN_FALLBACK_USERNAME") ?: ""
        val fallbackPass = properties.getProperty("TURN_FALLBACK_PASSWORD") ?: ""

        buildConfigField("String", "TURN_FALLBACK_URL", "\"$fallbackUrl\"")
        buildConfigField("String", "TURN_FALLBACK_USERNAME", "\"$fallbackUser\"")
        buildConfigField("String", "TURN_FALLBACK_PASSWORD", "\"$fallbackPass\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/proguard/androidx-*.pro"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.window)


    // Material Icons Extended
    implementation(libs.compose.material.icons.extended)
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material:material:1.6.0")

    // Navigation
    implementation(libs.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    // Firebase (Auth + Firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Modern Google Sign-In (replaces deprecated play-services-auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Legacy Google Sign-In (for older Android versions)
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Networking (ImgBB)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image Loading
    implementation(libs.coil.compose)

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Datastore (for theme preferences)
    implementation(libs.datastore.preferences)

    // Google Auth (for OAuth)
    implementation(libs.play.services.auth)

    // Emoji2
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-bundled:1.4.0")
    implementation("androidx.emoji2:emoji2-emojipicker:1.4.0")

    // Room Database (for local messaging storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (for background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
}
