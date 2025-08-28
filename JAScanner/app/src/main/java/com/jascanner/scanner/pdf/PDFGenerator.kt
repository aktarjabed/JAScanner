package com.jascanner.scanner.pdf

import android.content.Context
import android.graphics.Bitmap
import com.jascanner.security.LTVSignatureManager
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class PDFGenerator @Inject constructor(
    private val context: Context,
    private val pdfa: PDFAGenerator,
    private val signer: LTVSignatureManager
) {
    enum class Format { STANDARD, PDF_A_2U }
    data class Options(
        val format: Format = Format.PDF_A_2U,
        val title: String = "JAScanner Document",
        val embedOCR: Boolean = true,
        val compressImages: Boolean = true,
        val signWithLTV: Boolean = false,
        val tsaUrl: String = "http://timestamp.sectigo.com"
    )

    fun generate(
        images: List<Bitmap>,
        ocrText: List<String>,
        outputFile: File,
        options: Options = Options(),
        certificateChain: Array<java.security.cert.X509Certificate> = emptyArray()
    ): Boolean {
        return try {
            val ok = when (options.format) {
                Format.PDF_A_2U -> pdfa.generatePDFA(images, ocrText, outputFile,
                    PDFAGenerator.PDFAOptions(
                        title = options.title,
                        embedOCR = options.embedOCR,
                        compressImages = options.compressImages
                    )
                )
                Format.STANDARD -> {
                    pdfa.generatePDFA(images, ocrText, outputFile)
                }
            }
            if (!ok) return false
            if (options.signWithLTV && certificateChain.isNotEmpty()) {
                val temp = File(outputFile.parentFile, outputFile.nameWithoutExtension + "_signed.pdf")
                val signed = signer.signWithLTV(outputFile, temp, certificateChain, LTVSignatureManager.TSAConfig(options.tsaUrl))
                if (signed) {
                    outputFile.delete()
                    temp.renameTo(outputFile)
                } else {
                    temp.delete()
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Generate PDF failed")
            false
        }
    }

    fun convertLegacyToPDFA(inputFile: File, outputFile: File): Boolean = pdfa.convertToPDFA(inputFile, outputFile)
}

