// =============================================================================
// Alpha Blocker — root build file
//
// Only declares plugin versions. All real configuration is in app/build.gradle.kts.
//   - Android Gradle Plugin 8.7.3   (builds the APK)
//   - Kotlin 2.0.21                 (language)
//   - Kotlin Compose compiler 2.0.21 (needed for Jetpack Compose UI)
// =============================================================================

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
