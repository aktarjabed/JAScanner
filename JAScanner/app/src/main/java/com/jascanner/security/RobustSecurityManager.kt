package com.jascanner.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jascanner.core.ErrorHandler
import kotlinx.coroutines.*
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RobustSecurityManager(private val context: Context) {
    companion object {
        private const val TAG = "RobustSecurityManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "JAScanner_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AUTH_TIMEOUT_SECONDS = 30
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    private val securityScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
        ErrorHandler.createCoroutineExceptionHandler { errorType, message ->
            Log.e(TAG, "Security manager error: $errorType - $message")
        }
    )

    init {
        keyStore.load(null)
    }

    sealed class AuthenticationResult {
        object Success : AuthenticationResult()
        data class Error(val errorType: ErrorHandler.ErrorType, val message: String) : AuthenticationResult()
        object Cancelled : AuthenticationResult()
        object Failed : AuthenticationResult()
    }

    data class EncryptionResult(
        val encryptedData: ByteArray,
        val iv: ByteArray,
        val keyAlias: String
    )

    data class DecryptionResult(
        val decryptedData: ByteArray
    )

    enum class BiometricCapability {
        AVAILABLE, NO_HARDWARE, HARDWARE_UNAVAILABLE,
        NOT_ENROLLED, SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED, UNKNOWN
    }

    fun checkBiometricAvailability(): BiometricCapability {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricCapability.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricCapability.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricCapability.UNKNOWN
            else -> BiometricCapability.UNKNOWN
        }
    }

    suspend fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String
    ): AuthenticationResult {
        return suspendCoroutine { continuation ->
            ErrorHandler.safeExecute(
                onError = { errorType ->
                    continuation.resume(AuthenticationResult.Error(errorType, "Biometric authentication setup failed"))
                }
            ) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            Log.d(TAG, "Biometric authentication succeeded")
                            continuation.resume(AuthenticationResult.Success)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            Log.e(TAG, "Biometric authentication error: $errorCode - $errString")

                            val result = when (errorCode) {
                                BiometricPrompt.ERROR_USER_CANCELED -> AuthenticationResult.Cancelled
                                BiometricPrompt.ERROR_CANCELED -> AuthenticationResult.Cancelled
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthenticationResult.Cancelled
                                else -> AuthenticationResult.Error(
                                    ErrorHandler.ErrorType.Security,
                                    "Biometric error: $errString"
                                )
                            }
                            continuation.resume(result)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Log.w(TAG, "Biometric authentication failed")
                            // Don't resolve here - let user retry
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    suspend fun generateSecretKey(
        keyAlias: String,
        requireUserAuthentication: Boolean = true
    ): ErrorHandler.Result<String> {
        return ErrorHandler.safeExecuteAsync {
            Log.d(TAG, "Generating secret key: $keyAlias")

            val fullKeyAlias = KEY_ALIAS_PREFIX + keyAlias

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                fullKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(requireUserAuthentication)
                .setUserAuthenticationTimeout(AUTH_TIMEOUT_SECONDS)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            Log.d(TAG, "Secret key generated successfully: $fullKeyAlias")
            fullKeyAlias
        }
    }

    suspend fun encryptData(
        data: ByteArray,
        keyAlias: String
    ): ErrorHandler.Result<EncryptionResult> {
        return ErrorHandler.safeExecuteAsync {
            Log.d(TAG, "Encrypting data with key: $keyAlias")

            val fullKeyAlias = KEY_ALIAS_PREFIX + keyAlias

            if (!keyStore.containsAlias(fullKeyAlias)) {
                throw IllegalArgumentException("Key not found: $fullKeyAlias")
            }

            val secretKey = keyStore.getKey(fullKeyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)

            Log.d(TAG, "Data encrypted successfully")

            EncryptionResult(
                encryptedData = encryptedData,
                iv = iv,
                keyAlias = fullKeyAlias
            )
        }
    }

    suspend fun decryptData(
        encryptedData: ByteArray,
        iv: ByteArray,
        keyAlias: String
    ): ErrorHandler.Result<DecryptionResult> {
        return ErrorHandler.safeExecuteAsync {
            Log.d(TAG, "Decrypting data with key: $keyAlias")

            val fullKeyAlias = KEY_ALIAS_PREFIX + keyAlias

            if (!keyStore.containsAlias(fullKeyAlias)) {
                throw IllegalArgumentException("Key not found: $fullKeyAlias")
            }

            val secretKey = keyStore.getKey(fullKeyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedData = cipher.doFinal(encryptedData)

            Log.d(TAG, "Data decrypted successfully")

            DecryptionResult(decryptedData = decryptedData)
        }
    }

    fun generateSecureRandom(size: Int): ByteArray {
        val secureRandom = SecureRandom()
        val randomBytes = ByteArray(size)
        secureRandom.nextBytes(randomBytes)
        return randomBytes
    }

    suspend fun hashData(data: ByteArray): ErrorHandler.Result<ByteArray> {
        return ErrorHandler.safeExecuteAsync {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest(data)
        }
    }

    fun secureWipe(data: ByteArray) {
        try {
            val secureRandom = SecureRandom()
            repeat(3) {
                secureRandom.nextBytes(data)
            }
            data.fill(0)
            Log.d(TAG, "Data securely wiped")
        } catch (e: Exception) {
            Log.e(TAG, "Secure wipe failed", e)
        }
    }

    fun keyExists(keyAlias: String): Boolean {
        return try {
            val fullKeyAlias = KEY_ALIAS_PREFIX + keyAlias
            keyStore.containsAlias(fullKeyAlias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check key existence", e)
            false
        }
    }

    suspend fun deleteKey(keyAlias: String): ErrorHandler.Result<Boolean> {
        return ErrorHandler.safeExecuteAsync {
            Log.d(TAG, "Deleting key: $keyAlias")

            val fullKeyAlias = KEY_ALIAS_PREFIX + keyAlias

            if (keyStore.containsAlias(fullKeyAlias)) {
                keyStore.deleteEntry(fullKeyAlias)
                Log.d(TAG, "Key deleted successfully: $fullKeyAlias")
                true
            } else {
                Log.w(TAG, "Key not found: $fullKeyAlias")
                false
            }
        }
    }

    fun release() {
        try {
            Log.d(TAG, "Releasing security manager resources")
            securityScope.cancel()
            Log.d(TAG, "Security manager resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing security manager resources", e)
        }
    }
}