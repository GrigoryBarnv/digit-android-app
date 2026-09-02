pluginManagement {
    plugins {
        id("com.android.application") version "9.1.1"
        id("com.android.library") version "9.1.1"
        id("kotlin-android") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }
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
        mavenCentral()
    }
}

rootProject.name = "digit-app"
include(":app")
include(":libausbc")
include(":libnative")
include(":libuvc")
 
