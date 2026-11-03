// =============================================================================
// Alpha Blocker — app module build file
//
// AUDIT NOTES
//  * Dependencies are deliberately minimal: AndroidX core, Activity, Compose UI
//    and Kotlin coroutines. There is NO networking, analytics, crash-reporting
//    or ads library. You can confirm this by reading the `dependencies` block.
//  * minSdk 29 (Android 10) because Private DNS control and several Device
//    Owner restrictions used by this app were added in Android 10.
// =============================================================================

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alphablocker"
    compileSdk = 35

    defaultConfig {
        // The app's unique ID on Android and F-Droid. To publish, change ONLY this
        // line (e.g. "io.github.yourname.alphablocker"); the code package
        // (namespace above) stays com.alphablocker. The ADB commands shown in the
        // app are built from this ID automatically.
        applicationId = "com.alphablocker"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.16.4"
    }

    buildTypes {
        release {
            // Shrinking is off so the APK matches the source as closely as
            // possible, which makes auditing easier.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // F-Droid requirement: don't embed Google's encrypted dependency-info block
    // in the APK (it can't be verified and breaks reproducible builds).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Compose "bill of materials": pins all Compose libraries to matching versions.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
