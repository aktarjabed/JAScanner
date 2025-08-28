package com.jascanner.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.biometric.BiometricManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilitiesDetector @Inject constructor(
    private val context: Context
) {
    
    data class DeviceCapabilities(
        val hasCamera: Boolean = false,
        val hasFrontCamera: Boolean = false,
        val hasBackCamera: Boolean = false,
        val hasFlash: Boolean = false,
        val hasAutofocus: Boolean = false,
        val hasBiometric: Boolean = false,
        val hasFingerprint: Boolean = false,
        val hasFaceUnlock: Boolean = false,
        val hasTerahertzSupport: Boolean = false,
        val maxCameraResolution: String = "Unknown",
        val supportedImageFormats: List<String> = emptyList(),
        val deviceModel: String = Build.MODEL,
        val androidVersion: String = Build.VERSION.RELEASE,
        val apiLevel: Int = Build.VERSION.SDK_INT
    )

    private var _capabilities: DeviceCapabilities? = null

    fun getCapabilities(): DeviceCapabilities {
        if (_capabilities == null) {
            _capabilities = detectCapabilities()
        }
        return _capabilities!!
    }

    private fun detectCapabilities(): DeviceCapabilities {
        return DeviceCapabilities(
            hasCamera = hasCamera(),
            hasFrontCamera = hasFrontCamera(),
            hasBackCamera = hasBackCamera(),
            hasFlash = hasFlash(),
            hasAutofocus = hasAutofocus(),
            hasBiometric = hasBiometric(),
            hasFingerprint = hasFingerprint(),
            hasFaceUnlock = hasFaceUnlock(),
            hasTerahertzSupport = hasTerahertzSupport(),
            maxCameraResolution = getMaxCameraResolution(),
            supportedImageFormats = getSupportedImageFormats()
        )
    }

    private fun hasCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun hasFrontCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun hasBackCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    private fun hasFlash(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun hasAutofocus(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)
    }

    private fun hasBiometric(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    private fun hasFingerprint(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        } else {
            false
        }
    }

    private fun hasFaceUnlock(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        } else {
            false
        }
    }

    private fun hasTerahertzSupport(): Boolean {
        // Check for custom hardware or specific device models that support THz
        return when {
            Build.MODEL.contains("THZ", ignoreCase = true) -> true
            Build.MANUFACTURER.contains("THz", ignoreCase = true) -> true
            // Add specific device models that support THz scanning
            Build.MODEL in listOf("Galaxy THz", "Pixel THz") -> true
            else -> false
        }
    }

    private fun getMaxCameraResolution(): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList
            
            var maxResolution = "Unknown"
            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                
                outputSizes?.let { sizes ->
                    val maxSize = sizes.maxByOrNull { it.width * it.height }
                    maxSize?.let {
                        val resolution = "${it.width}x${it.height}"
                        if (maxResolution == "Unknown" || 
                            (it.width * it.height) > parseResolution(maxResolution)) {
                            maxResolution = resolution
                        }
                    }
                }
            }
            maxResolution
        } catch (e: Exception) {
            Timber.e(e, "Failed to get camera resolution")
            "Unknown"
        }
    }

    private fun parseResolution(resolution: String): Int {
        return try {
            val parts = resolution.split("x")
            if (parts.size == 2) {
                parts[0].toInt() * parts[1].toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getSupportedImageFormats(): List<String> {
        return try {
            val formats = mutableListOf<String>()
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList
            
            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputFormats = map?.outputFormats
                
                outputFormats?.forEach { format ->
                    val formatName = when (format) {
                        android.graphics.ImageFormat.JPEG -> "JPEG"
                        android.graphics.ImageFormat.YUV_420_888 -> "YUV_420_888"
                        android.graphics.ImageFormat.NV21 -> "NV21"
                        android.graphics.ImageFormat.NV16 -> "NV16"
                        else -> "FORMAT_$format"
                    }
                    if (!formats.contains(formatName)) {
                        formats.add(formatName)
                    }
                }
            }
            formats.toList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get supported image formats")
            listOf("JPEG") // Default fallback
        }
    }

    fun logCapabilities() {
        val capabilities = getCapabilities()
        Timber.i("Device Capabilities:")
        Timber.i("- Device: ${capabilities.deviceModel}")
        Timber.i("- Android: ${capabilities.androidVersion} (API ${capabilities.apiLevel})")
        Timber.i("- Camera: ${capabilities.hasCamera}")
        Timber.i("- Front Camera: ${capabilities.hasFrontCamera}")
        Timber.i("- Back Camera: ${capabilities.hasBackCamera}")
        Timber.i("- Flash: ${capabilities.hasFlash}")
        Timber.i("- Autofocus: ${capabilities.hasAutofocus}")
        Timber.i("- Biometric: ${capabilities.hasBiometric}")
        Timber.i("- Fingerprint: ${capabilities.hasFingerprint}")
        Timber.i("- Face Unlock: ${capabilities.hasFaceUnlock}")
        Timber.i("- THz Support: ${capabilities.hasTerahertzSupport}")
        Timber.i("- Max Resolution: ${capabilities.maxCameraResolution}")
        Timber.i("- Image Formats: ${capabilities.supportedImageFormats}")
    }
}