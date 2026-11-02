// =============================================================================
// Alpha Blocker — Gradle settings
//
// Declares where Gradle downloads build plugins and libraries from (Google's
// Maven repo and Maven Central). These downloads happen ONLY on your computer
// while building. The finished app itself has no internet permission.
// =============================================================================

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Fail the build if a module tries to add its own repositories.
    // Keeps the list of download sources small and auditable.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AlphaBlocker"
include(":app")
