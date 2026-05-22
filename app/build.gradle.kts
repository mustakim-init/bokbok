import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
//    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mustakim.bokbok"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mustakim.bokbok"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        androidResources {
            localeFilters += "en"
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

        // Groq AI
        val groqApiKey = properties.getProperty("GROQ_API_KEY") ?: ""
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")

        buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"  // ← add this
                )
            }
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
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
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${project.rootDir.absolutePath}/app/compose_stability.conf"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/proguard/androidx-*.pro"
            excludes += "/META-INF/CONTRIBUTORS.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}


dependencies {
    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.preference)

    // Lifecycle
    implementation(libs.bundles.lifecycle)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

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

    // Media3
    implementation("androidx.media3:media3-transformer:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

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
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.androidx.animation)
    ksp(libs.room.compiler)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager (for background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)

    // Shizuku (for ADB/Root operations)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Oboe (for low-latency native audio)
    implementation("com.google.oboe:oboe:1.9.3")

    // ONNX Runtime provided by Sherpa-ONNX AAR
    
    // Manual local AAR/JAR support (Primary for Sherpa-ONNX)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // CameraX (for Facecam)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // NanoHTTPD (for Wi-Fi File Sharing)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Bouncy Castle (for ADB key management)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    
    // Libadb (Pure Java ADB)
    implementation(libs.libadb.android) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // Hidden API Bypass (required by libadb-android for Conscrypt.exportKeyingMaterial)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // Baseline Profile
    implementation(libs.androidx.profileinstaller)
//    "baselineProfile"(project(":baselineprofile"))

    // ==========================================
    // Project Modules
    // ==========================================
    implementation(project(":core"))
    implementation(project(":feature:music"))
    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lastfm"))

    // Shimmer (used in app module's ShimmerHost and NavGraph)
    implementation(libs.shimmer)

    // Media3 Session (needed for MusicService binder in app module)
    implementation(libs.media3.session)

    // Ktor (needed for AboutScreen HTTP client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Ensure ProcessLifecycleOwner is available for the presence manager
    implementation("com.github.therealbush:translator:1.1.1")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
}
