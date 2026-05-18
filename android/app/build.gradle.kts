import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
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

        val props = Properties().apply {
            load(FileInputStream(rootProject.file("local.properties")))
        }
        buildConfigField("String", "API_BASE_URL", "\"${props["API_BASE_URL"]}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${props["GROQ_API_KEY"] ?: ""}\"")
        packaging {
            resources {
                excludes += "META-INF/DEPENDENCIES"
                excludes += "META-INF/LICENSE"
                excludes += "META-INF/LICENSE.txt"
                excludes += "META-INF/NOTICE"
                excludes += "META-INF/NOTICE.txt"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../akiba-release.jks")
            storePassword = "Mark@02"
            keyAlias = "akiba-key"
            keyPassword = "Mark@02"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            firebaseAppDistribution {
                releaseNotes = "Latest Akiba build"
                groups = "testers"
            }
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:${libs.versions.navigation.get()}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycle.get()}")
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.squareup.retrofit2:retrofit:${libs.versions.retrofit.get()}")
    implementation("com.squareup.retrofit2:converter-gson:${libs.versions.retrofit.get()}")
    implementation("com.squareup.okhttp3:logging-interceptor:${libs.versions.okhttp.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.coroutines.get()}")
    implementation("androidx.datastore:datastore-preferences:${libs.versions.datastore.get()}")
    implementation("io.coil-kt:coil-compose:${libs.versions.coil.get()}")
    implementation("com.patrykandpatrick.vico:compose-m3:${libs.versions.vico.get()}")
    implementation("androidx.biometric:biometric:${libs.versions.biometric.get()}")
    implementation("androidx.core:core-splashscreen:${libs.versions.splashscreen.get()}")
    implementation("com.google.accompanist:accompanist-systemuicontroller:${libs.versions.accompanist.get()}")
    implementation("androidx.paging:paging-compose:${libs.versions.paging.get()}")
    implementation("com.google.firebase:firebase-appdistribution-gradle:5.0.0")
}