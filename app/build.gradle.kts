plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase is optional. The app builds and runs without google-services.json;
// Google sign in and cloud backup simply stay switched off until it is added.
val firebaseConfig = file("google-services.json")
if (firebaseConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.poodicraft.bookquest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.poodicraft.bookquest"
        minSdk = 24
        targetSdk = 34
        versionCode = 13
        versionName = "2.2"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // A checked in debug key keeps the signing fingerprint stable across
            // machines and CI runs, which is what Google sign in is registered against.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // A shipped build is shrunk and obfuscated. proguard-rules.pro holds
            // the keep rules the reflective libraries need — Firestore reads and
            // writes model classes by field name, so stripping those names breaks
            // the backup in a way that only shows up on a release build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Pinned to 2.7.0 deliberately, and it must stay in step with the Compose
    // BOM below.
    //
    // Lifecycle 2.8 introduced its own androidx.lifecycle.compose.LocalLifecycleOwner,
    // separate from the androidx.compose.ui.platform one, and only Compose UI
    // 1.7 onwards provides it. The BOM here is Compose UI 1.6.8, so every
    // collectAsStateWithLifecycle was reading a composition local that nothing
    // had ever supplied — "CompositionLocal LocalLifecycleOwner not present".
    // 2.7.0 reads the Compose UI one, which 1.6.8 does provide.
    //
    // Moving to lifecycle 2.8 again means Compose UI 1.7+, which means
    // compileSdk 35 and a newer Android Gradle plugin. All four move together
    // or none of them do.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Draws the launch screen before any of our code runs, so tapping the icon
    // does not open on a blank rectangle.
    implementation("androidx.core:core-splashscreen:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Google sign in through Credential Manager.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Account and cloud backup of reading progress.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
