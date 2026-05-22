plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mustakim.bokbok.music"
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

    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
        buildConfigField("String", "ARCHITECTURE", "\"universal\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    // Compose
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons.extended)
    implementation("androidx.compose.material:material:1.6.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
    implementation("androidx.window:window:1.3.0")
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ArchiveTune Dependencies
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.palette)
    implementation(libs.multiplatform.markdown)
    implementation(libs.shimmer)
    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation(libs.squigglyslider)
    implementation(libs.compose.reorderable)

    implementation(libs.kuromoji.ipadic)
    implementation(libs.anyascii)
    implementation(libs.apache.lang3)

    implementation(libs.jsoup)
    implementation(libs.re2j)

    // Local modules required for music
    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":lastfm"))
    implementation(project(":betterlyrics"))
    implementation(project(":kizzy"))
    implementation(project(":simpmusic"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    
    implementation("com.github.Kyant0:m3color:2025.4")

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)

    // Image Loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)
    
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    ksp(libs.room.compiler)
    
    // DataStore
    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // WorkManager (for background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.translator)
}
