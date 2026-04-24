import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("kotlin-kapt")
}

android {
    namespace     = "com.akiba.app"
    compileSdk    = 35

    defaultConfig {
        applicationId = "com.akiba.app"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        // Read API_BASE_URL from local.properties
        val props = Properties().apply {
            load(FileInputStream(rootProject.file("local.properties")))
        }
        buildConfigField("String", "API_BASE_URL", "\"${props["API_BASE_URL"]}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${props["GEMINI_API_KEY"] ?: ""}\"")
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ── Compose BOM — pins all Compose versions in one shot ───────────────
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Core ──────────────────────────────────────────────────────────────
    implementation("androidx.activity:activity-compose:1.9.3")

    // ── Navigation ────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:${libs.versions.navigation.get()}")

    // ── Lifecycle + ViewModel ─────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycle.get()}")

    // ── Hilt — dependency injection ───────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Networking ────────────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:${libs.versions.retrofit.get()}")
    implementation("com.squareup.retrofit2:converter-gson:${libs.versions.retrofit.get()}")
    implementation("com.squareup.okhttp3:logging-interceptor:${libs.versions.okhttp.get()}")

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.coroutines.get()}")

    // ── DataStore — replaces SharedPreferences ────────────────────────────
    implementation("androidx.datastore:datastore-preferences:${libs.versions.datastore.get()}")

    // ── Image loading ─────────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:${libs.versions.coil.get()}")

    // ── Charts ────────────────────────────────────────────────────────────
    implementation("com.patrykandpatrick.vico:compose-m3:${libs.versions.vico.get()}")

    // ── Biometric auth ────────────────────────────────────────────────────
    implementation("androidx.biometric:biometric:${libs.versions.biometric.get()}")

    // ── Splash screen ─────────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:${libs.versions.splashscreen.get()}")

    // ── System UI (status/nav bar color control) ──────────────────────────
    implementation("com.google.accompanist:accompanist-systemuicontroller:${libs.versions.accompanist.get()}")

    // ── Paging 3 — infinite scroll ────────────────────────────────────────
    implementation("androidx.paging:paging-compose:${libs.versions.paging.get()}")
}