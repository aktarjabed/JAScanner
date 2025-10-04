package com.jascanner.core.signature

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class PadesSignatureManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DigitalSignatureManager {
    override suspend fun sign(file: File): File {
        Timber.w("Digital signature is not yet implemented. Returning original file.")
        // In a real implementation, this would involve using a library like iText
        // to apply a PAdES (PDF Advanced Electronic Signatures) compliant digital signature.
        // This would require a private key from the Android Keystore and a certificate.
        return file
    }
}