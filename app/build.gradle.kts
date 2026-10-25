plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fighter.studykeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fighter.studykeyboard"
        minSdk = 21
        targetSdk = 34
        versionCode = 9
        versionName = "1.8"
    }

    // Fixed signing key so every build updates the previous install in place
    // (debug keys are regenerated on every CI machine, which breaks updates).
    signingConfigs {
        create("study") {
            storeFile = rootProject.file("keystore/fighter-study.p12")
            storePassword = "fighter-study-2026"
            keyAlias = "fighter"
            keyPassword = "fighter-study-2026"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("study")
        }
        release {
            signingConfig = signingConfigs.getByName("study")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    // Intentionally empty: framework UI only (small APK, fast builds).
}
