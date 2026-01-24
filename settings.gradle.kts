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
include(":app", ":baselineprofile")
