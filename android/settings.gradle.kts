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
        mavenCentral()
    }
}

rootProject.name = "Trailify"

include(":app")

// Wspólne
include(":core-domain")
include(":core-ui")
include(":core-data")

// Funkcje - Kacper
include(":feature-map")
include(":feature-routes")
include(":feature-recording")

// Funkcje - Mikołaj
include(":feature-auth")
include(":feature-discover")
include(":feature-community")
include(":feature-profile")
