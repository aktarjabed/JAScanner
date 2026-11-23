plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so there is no kotlin-android plugin here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "aktarjabed.com.jaascanner"
    // Compose 1.12 (BOM 2026.09.00) requires compileSdk 37 and AGP 9.2+.
    compileSdk = 37

    defaultConfig {
        applicationId = "aktarjabed.com.jaascanner"
        minSdk = 32
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    // Built-in camera fallback for phones where Google's document scanner is not installed.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Scanning, OCR, barcodes
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.code.scanner)
    implementation(libs.zxing.core)

    // PDF editing (merge, split, protect, stamp). Viewing uses the platform PdfRenderer.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
