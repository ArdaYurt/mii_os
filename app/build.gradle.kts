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
        // Mi Box S 2nd gen runs Android 11 (API 30). Glass blur needs Android 12 (API 31),
        // so on older boxes the glass panels fall back to a frosted tint (see Glass.kt).
        minSdk = 30
        targetSdk = 35
        // CI numbers every build, so the in-app updater can tell which one is newer.
        val build = System.getenv("MII_VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.2.$build"
    }

    signingConfigs {
        // CI fills these from repository secrets so every build can update the last one in place.
        // Without them, release builds fall back to the debug key.
        create("release") {
            val path = System.getenv("MII_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("MII_KEYSTORE_PASSWORD")
                // tools/signing/make-key.ps1 makes one key named mii-os, with the keystore's password
                keyAlias = System.getenv("MII_KEY_ALIAS")?.takeIf { it.isNotEmpty() } ?: "mii-os"
                keyPassword = System.getenv("MII_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: storePassword
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
    // The TV channel's player: live HLS and MPEG-TS streams, with the box's hardware decoders.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
}
