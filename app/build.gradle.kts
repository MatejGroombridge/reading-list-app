import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load keystore.properties (local dev). In CI, environment variables take precedence.
// The file should look like:
//   storeFile=/Users/you/Documents/release.jks
//   storePassword=...
//   keyAlias=main
//   keyPassword=...
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "dev.matejgroombridge.readinglist"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.matejgroombridge.readinglist"
        minSdk = 26          // Android 8.0+ (covers ~95% of devices, allows modern APIs)
        targetSdk = 35       // Android 15
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        create("release") {
            // CI path: keystore is decoded by the workflow into a temp file, and
            // its location + passwords are passed in as env vars.
            val ciStorePath = System.getenv("KEYSTORE_PATH")
            if (ciStorePath != null) {
                storeFile = file(ciStorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                // Local dev path: read from keystore.properties (gitignored).
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
            // If neither is present, this signing config remains unconfigured and
            // assembleRelease will fail clearly rather than silently sign with the
            // debug key.
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android + Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Persistence
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Book cover loading. Coil streams the cover JPEGs from
    // covers.openlibrary.org and handles its own memory/disk cache, so the
    // grid stays smooth without us managing bitmaps by hand.
    implementation(libs.coil.compose)

    // Open Library search. OkHttp engine because it's the best-supported
    // one on Android; content negotiation wires kotlinx-serialization in
    // so responses decode straight into our DTOs.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Unit tests for the genre classifier. Its rule table is exactly the kind
    // of thing that regresses silently when a keyword is added, so it's worth
    // pinning the behaviour down. Test-only — nothing here reaches the APK.
    testImplementation(libs.junit)
}
