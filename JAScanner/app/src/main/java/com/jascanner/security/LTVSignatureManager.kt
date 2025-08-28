package com.jascanner.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.StampingProperties
import com.itextpdf.signatures.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LTVSignatureManager @Inject constructor(private val context: Context) {
    companion object {
        private const val KEY_ALIAS = "JAScanner_Sign_EC_P256"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    data class TSAConfig(val url: String, val username: String? = null, val password: String? = null)

    fun signWithLTV(
        inputFile: File,
        outputFile: File,
        certificateChain: Array<X509Certificate>,
        tsa: TSAConfig
    ): Boolean {
        return try {
            val privateKey = getOrCreateKey()
            val reader = PdfReader(inputFile)
            val signer = PdfSigner(reader, outputFile.outputStream(), StampingProperties())

            val rect = Rectangle(36f, 36f, 200f, 60f)
            val appearance = signer.signatureAppearance
                .setReason("Scanned & approved via JAScanner")
                .setLocation("Android Keystore")
                .setPageRect(rect).setPageNumber(1)
                .setLayer2Text("Digitally signed by JAScanner")

            val fieldName = "SIG_${System.currentTimeMillis()}"
            signer.setFieldName(fieldName)

            val signature = PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, ANDROID_KEYSTORE)
            val ocsp = OcspClientBouncyCastle(null)
            val tsaClient = TSAClientBouncyCastle(tsa.url, tsa.username, tsa.password)

            MakeSignature.signDetached(
                appearance,
                signature,
                certificateChain,
                null,
                ocsp,
                tsaClient,
                0,
                PdfSigner.CryptoStandard.CADES
            )

            addLTV(outputFile, fieldName)
            true
        } catch (e: Exception) {
            Timber.e(e, "Signing with LTV failed")
            false
        }
    }

    private fun addLTV(signedFile: File, sigName: String) {
        val reader = PdfReader(signedFile)
        val stamper = PdfSigner(reader, signedFile.outputStream(), StampingProperties().useAppendMode())
        val enforcer = LtvEnforcer(reader, stamper)
        enforcer.addVerification(
            sigName,
            LtvVerification.CertificateOption.SIGNING_CERTIFICATE,
            LtvVerification.Level.OCSP_CRL,
            LtvVerification.CertificateInclusion.YES
        )
        stamper.close()
        reader.close()
    }

    fun verifyWithLTV(file: File): Pair<Boolean, String> {
        return try {
            val doc = com.itextpdf.kernel.pdf.PdfDocument(PdfReader(file))
            val util = SignatureUtil(doc)
            val names = util.signatureNames
            if (names.isEmpty()) return false to "No signatures found"
            val results = buildString {
                for (n in names) {
                    val pkcs7 = util.readSignatureData(n)
                    val ok = pkcs7.verifySignatureIntegrityAndAuthenticity()
                    val ltv = LtvVerifier(doc).verify(n).isOk
                    append("$n: sig=${ok}, ltv=${ltv}\n")
                }
            }
            doc.close()
            (results.contains("sig=true") && results.contains("ltv=true")) to results
        } catch (e: Exception) {
            false to "Verification failed: ${e.message}"
        }
    }

    private fun getOrCreateKey(): PrivateKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return ks.getKey(KEY_ALIAS, null) as PrivateKey
        }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(300)
            .build()
        kpg.initialize(spec)
        val pair = kpg.generateKeyPair()
        return pair.private
    }
}

