package com.jascanner.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BiometricHelper @Inject constructor(
    private val context: Context
) {
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Timber.d("No biometric hardware available")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Timber.d("Biometric hardware unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Timber.d("No biometric credentials enrolled")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Timber.d("Security update required for biometrics")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Timber.d("Biometric authentication unsupported")
                false
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Timber.d("Biometric status unknown")
                false
            }
            else -> false
        }
    }

    fun getBiometricCapabilities(): BiometricCapabilities {
        val biometricManager = BiometricManager.from(context)
        
        val strongBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val weakBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val deviceCredential = biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        
        return BiometricCapabilities(
            hasStrongBiometric = strongBiometric,
            hasWeakBiometric = weakBiometric,
            hasDeviceCredential = deviceCredential,
            isAvailable = strongBiometric || weakBiometric || deviceCredential
        )
    }

    suspend fun authenticate(
        activity: FragmentActivity? = null,
        title: String = "Biometric Authentication",
        subtitle: String = "Use your biometric credential to authenticate",
        description: String = "Authentication is required to access this feature",
        allowDeviceCredential: Boolean = true
    ): SecurityManager.BiometricResult {
        if (activity == null) {
            return SecurityManager.BiometricResult.Error("Activity required for biometric authentication")
        }

        if (!isBiometricAvailable()) {
            return SecurityManager.BiometricResult.Error("Biometric authentication not available")
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            
            val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Timber.d("Biometric authentication succeeded")
                    if (continuation.isActive) {
                        continuation.resume(SecurityManager.BiometricResult.Success)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.d("Biometric authentication failed")
                    if (continuation.isActive) {
                        continuation.resume(SecurityManager.BiometricResult.Failed)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            Timber.d("Biometric authentication cancelled")
                            if (continuation.isActive) {
                                continuation.resume(SecurityManager.BiometricResult.Cancelled)
                            }
                        }
                        else -> {
                            Timber.e("Biometric authentication error: $errorCode - $errString")
                            if (continuation.isActive) {
                                continuation.resume(SecurityManager.BiometricResult.Error(errString.toString()))
                            }
                        }
                    }
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .apply {
                    if (allowDeviceCredential) {
                        setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                    } else {
                        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        setNegativeButtonText("Cancel")
                    }
                }
                .build()

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                Timber.e(e, "Failed to show biometric prompt")
                if (continuation.isActive) {
                    continuation.resume(SecurityManager.BiometricResult.Error(e.message ?: "Authentication failed"))
                }
            }
        }
    }

    fun createCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            // Create a crypto object for cryptographic operations
            // This would be used for more secure authentication scenarios
            null // Simplified for now
        } catch (e: Exception) {
            Timber.e(e, "Failed to create crypto object")
            null
        }
    }

    data class BiometricCapabilities(
        val hasStrongBiometric: Boolean,
        val hasWeakBiometric: Boolean,
        val hasDeviceCredential: Boolean,
        val isAvailable: Boolean
    )
}