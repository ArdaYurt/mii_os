plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.miios.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miios.launcher"
        // Mi Box S 2nd gen ships Google TV on Android 12 (API 31), which is also
        // the first version with RenderEffect blur for the glass panels.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // CI fills these from repository secrets so every build can update the last one in place.
        // Without them, release builds fall back to the debug key.
        create("release") {
            val path = System.getenv("MII_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("MII_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MII_KEY_ALIAS")
                keyPassword = System.getenv("MII_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Speed is the first requirement: release builds are minified and optimized.
            // Debug builds of Compose run noticeably slower; don't judge performance on them.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("MII_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Installs the baseline profiles that Compose ships, so a sideloaded APK
    // starts fast without waiting for the Play Store to compile it.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
