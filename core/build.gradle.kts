plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mustakim.bokbok.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons.extended)
    implementation("androidx.compose.material:material:1.6.0")
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Datastore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.bundles.coroutines)
    
    // Shimmer
    implementation(libs.shimmer)
    
    // Palette
    implementation(libs.palette)
    
    // Serialization & JSON
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.gson)
    
    // M3Color
    implementation("com.github.Kyant0:m3color:2025.4")
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // Coil
    implementation(libs.coil.compose)

    // Ktor (for Updater)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
}
