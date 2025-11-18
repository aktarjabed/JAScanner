plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.aktarjabed.jascanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aktarjabed.jascanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    // ═══════════════════════════════════════════════════════════════════════
    // Core Android Libraries
    // ═══════════════════════════════════════════════════════════════════════
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle & ViewModel
    // ═══════════════════════════════════════════════════════════════════════
    val lifecycleVersion = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")

    // ═══════════════════════════════════════════════════════════════════════
    // CameraX - Document Scanning
    // ═══════════════════════════════════════════════════════════════════════
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ═══════════════════════════════════════════════════════════════════════
    // OpenCV - Document Detection & Image Processing
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.opencv:opencv-android:4.8.0")

    // ═══════════════════════════════════════════════════════════════════════
    // ML Kit - OCR Text Recognition
    // ═══════════════════════════════════════════════════════════════════════
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // ═══════════════════════════════════════════════════════════════════════
    // PDF Generation - iText 7
    // ═══════════════════════════════════════════════════════════════════════
    val itextVersion = "7.2.5"
    implementation("com.itextpdf:itext7-core:$itextVersion")
    implementation("com.itextpdf:pdfa:$itextVersion")
    implementation("com.itextpdf:sign:$itextVersion")

    // ═══════════════════════════════════════════════════════════════════════
    // Cryptography - BouncyCastle
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // ═══════════════════════════════════════════════════════════════════════
    // Database - Room
    // ═══════════════════════════════════════════════════════════════════════
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ═══════════════════════════════════════════════════════════════════════
    // DataStore - Preferences
    // ═══════════════════════════════════════════════════════════════════════
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ═══════════════════════════════════════════════════════════════════════
    // Security & Biometric
    // ═══════════════════════════════════════════════════════════════════════
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ═══════════════════════════════════════════════════════════════════════
    // WorkManager - Background Tasks
    // ═══════════════════════════════════════════════════════════════════════
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ═══════════════════════════════════════════════════════════════════════
    // Image Loading - Glide
    // ═══════════════════════════════════════════════════════════════════════
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:compiler:4.16.0")

    // ═══════════════════════════════════════════════════════════════════════
    // Logging - Timber
    // ═══════════════════════════════════════════════════════════════════════
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ═══════════════════════════════════════════════════════════════════════
    // Coroutines
    // ═══════════════════════════════════════════════════════════════════════
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ═══════════════════════════════════════════════════════════════════════
    // Testing
    // ═══════════════════════════════════════════════════════════════════════
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.room:room-testing:$roomVersion")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.camera:camera-testing:$cameraxVersion")
}