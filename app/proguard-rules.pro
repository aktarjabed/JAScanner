# JAScanner ProGuard/R8 Rules
# Generated for production hardening

# ==================== ESSENTIAL KEEP RULES ====================

# Keep your application entry points
-keep class com.jascanner.MainActivity { *; }
-keep class com.jascanner.** extends android.app.Activity
-keep class com.jascanner.** extends androidx.appcompat.app.AppCompatActivity

# Keep all Fragments
-keep class * extends androidx.fragment.app.Fragment { *; }

# Keep custom Views
-keep public class * extends android.view.View {
public <init>(android.content.Context);
public <init>(android.content.Context, android.util.AttributeSet);
public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==================== JETPACK COMPOSE RULES ====================

-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class androidx.compose.** {
*;
}

# Keep composable functions
-keep @androidx.compose.runtime.Composable public class *
-keepclasseswithmembers class * {
@androidx.compose.runtime.Composable *;
}

# ==================== KOTLIN SPECIFIC RULES ====================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlin intrinsics
-keep class kotlin.jvm.internal.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
volatile <fields>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    public static final *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==================== ROOM DATABASE RULES ====================

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
public static ** getDatabase(...);
}

# Keep DAOs
-keep interface * extends androidx.room.Dao
-keep class com.jascanner.data.local.dao.** { *; }

# Keep database entities
-keep @androidx.room.Entity class * { *; }
-keep class com.jascanner.data.local.entity.** { *; }

# ==================== HILT/DAGGER DEPENDENCY INJECTION ====================

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Dagger
-dontwarn com.google.errorprone.annotations.*
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }

# ==================== RETROFIT & NETWORKING ====================

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
@retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
@retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson (if used)
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
@com.google.gson.annotations.SerializedName <fields>;
}

# Keep data models for serialization
-keep class com.jascanner.data.model.** { *; }
-keep class com.jascanner.domain.model.** { *; }

# ==================== ML KIT / OPENCV / OCR ====================

# Google ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# OpenCV
-keep class org.opencv.** { *; }
-keepclassmembers class * {
native <methods>;
}

# Tesseract OCR (if used)
-keep class com.googlecode.tesseract.** { *; }

# ==================== CAMERA X ====================

-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# ==================== SECURITY & ENCRYPTION ====================

# Keep security-related classes (custom encryption)
-keep class com.jascanner.security.** { *; }
-keep class com.jascanner.data.crypto.** { *; }

# Android KeyStore
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }

# BiometricPrompt
-keep class androidx.biometric.** { *; }

# ==================== WORKERS & BACKGROUND TASKS ====================

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker {
public <init>(...);
}
-keep class com.jascanner.workers.** { *; }

# ==================== SERIALIZATION & PARCELABLE ====================

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
public static final ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
static final long serialVersionUID;
private static final java.io.ObjectStreamField[] serialPersistentFields;
private void writeObject(java.io.ObjectOutputStream);
private void readObject(java.io.ObjectInputStream);
java.lang.Object writeReplace();
java.lang.Object readResolve();
}

# ==================== REFLECTION & DYNAMIC CODE ====================

# Keep classes accessed via reflection
-keepclassmembers class * {
@androidx.annotation.Keep *;
}

# Keep dynamically loaded resources
-keep class **.R$* {
public static <fields>;
}

# ==================== PDF/DOCUMENT GENERATION ====================

# iText or similar PDF libraries
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Apache POI (if used for document handling)
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# ==================== OPTIMIZATION SETTINGS ====================

# Optimization options
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ==================== REMOVE LOGGING IN RELEASE ====================

# Remove logging calls
-assumenosideeffects class android.util.Log {
public static *** d(...);
public static *** v(...);
public static *** i(...);
public static *** w(...);
public static *** e(...);
}

# ==================== WARNINGS SUPPRESSION ====================

# Suppress warnings from third-party libraries
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# ==================== NATIVE METHODS ====================

# Keep native methods
-keepclasseswithmembernames class * {
native <methods>;
}

# ==================== ENUMS ====================

# Keep enum classes
-keepclassmembers enum * {
public static **[] values();
public static ** valueOf(java.lang.String);
}
