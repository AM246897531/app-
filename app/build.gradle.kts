plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.offlinehandwriting"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offlinehandwriting"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}
