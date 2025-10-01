package com.jascanner.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val context: Context,
    private val biometricHelper: BiometricHelper
) {
    
    companion object {
        private const val KEYSTORE_ALIAS = "JAScanner_Master_Key"
        private const val ENCRYPTED_PREFS_NAME = "secure_preferences"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(300) // 5 minutes
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isSecuritySetup(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            Timber.e(e, "Error checking security setup")
            false
        }
    }

    fun setupSecurity(): Boolean {
        return try {
            if (isSecuritySetup()) {
                return true
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(300)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            Timber.i("Security setup completed")
            true
        } catch (e: Exception) {
            Timber.e(e, "Security setup failed")
            false
        }
    }

    fun encryptData(data: ByteArray): EncryptedData? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                Timber.e("Keystore alias not found. Cannot encrypt.")
                return null
            }
            
            val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(data)
            val iv = cipher.iv
            
            EncryptedData(encryptedBytes, iv)
        } catch (e: Exception) {
            Timber.e(e, "Data encryption failed")
            null
        }
    }

    fun decryptData(encryptedData: EncryptedData): ByteArray? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                Timber.e("Keystore alias not found. Cannot decrypt.")
                return null
            }
            
            val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, encryptedData.iv))
            
            cipher.doFinal(encryptedData.encryptedBytes)
        } catch (e: Exception) {
            Timber.e(e, "Data decryption failed")
            null
        }
    }

    fun storeSecureValue(key: String, value: String) {
        try {
            encryptedPrefs.edit()
                .putString(key, value)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to store secure value")
        }
    }

    fun getSecureValue(key: String): String? {
        return try {
            encryptedPrefs.getString(key, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve secure value")
            null
        }
    }

    fun removeSecureValue(key: String) {
        try {
            encryptedPrefs.edit()
                .remove(key)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove secure value")
        }
    }

    fun clearAllSecureData() {
        try {
            encryptedPrefs.edit()
                .clear()
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear secure data")
        }
    }

    fun isBiometricAvailable(): Boolean {
        return biometricHelper.isBiometricAvailable()
    }

    suspend fun authenticateWithBiometric(): BiometricResult {
        return biometricHelper.authenticate()
    }

    fun generateSecureHash(data: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(data.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Hash generation failed")
            ""
        }
    }

    fun verifyHash(data: String, hash: String): Boolean {
        val computedHash = generateSecureHash(data)
        return computedHash.equals(hash, ignoreCase = true)
    }

    data class EncryptedData(
        val encryptedBytes: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedData

            if (!encryptedBytes.contentEquals(other.encryptedBytes)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = encryptedBytes.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    sealed class BiometricResult {
        object Success : BiometricResult()
        object Failed : BiometricResult()
        object Cancelled : BiometricResult()
        data class Error(val message: String) : BiometricResult()
    }
}