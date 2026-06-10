pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://k2-fsa.github.io/sherpa-onnx/android/AAR") }
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "BokBok"
include(":app")
// include(":baselineprofile")
include(":innertube")
include(":simpmusic")
include(":betterlyrics")
include(":kizzy")
include(":lrclib")
include(":lastfm")
include(":kugou")
include(":shazamkit")
include(":canvas")
include(":core")
include(":feature:music")
