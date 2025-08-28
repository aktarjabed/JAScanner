package com.jascanner.scanner.pdf

import android.content.Context
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteArrayOutputStream
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfVersion
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.pdfa.PdfAConformanceLevel
import com.itextpdf.pdfa.PdfADocument
import timber.log.Timber
import java.awt.color.ICC_Profile
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PDFAGenerator @Inject constructor(private val context: Context) {

    data class PDFAOptions(
        val title: String = "JAScanner Document",
        val author: String = "JAScanner",
        val creator: String = "JAScanner 1.0.0",
        val embedOCR: Boolean = true,
        val compressImages: Boolean = true
    )

    fun generatePDFA(images: List<Bitmap>, ocrText: List<String>, outputFile: File, options: PDFAOptions = PDFAOptions()): Boolean {
        return try {
            val writerProps = WriterProperties()
                .addXmpMetadata()
                .setPdfVersion(PdfVersion.PDF_2_0)

            val icc = loadSRGB()
            val pdfa = PdfADocument(outputFile.outputStream(), writerProps, PdfAConformanceLevel.PDF_A_2U, icc)
            val info = pdfa.documentInfo
            info.title = options.title
            info.author = options.author
            info.creator = options.creator
            info.producer = "JAScanner PDF/A"

            val doc = Document(pdfa)
            val font = loadEmbeddedFont()

            doc.add(
                Paragraph(options.title).setFont(font).setFontSize(16f).setBold().setTextAlignment(TextAlignment.CENTER)
            )
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            doc.add(Paragraph("Created: $now").setFont(font).setFontSize(10f).setTextAlignment(TextAlignment.RIGHT))

            images.forEachIndexed { idx, bmp ->
                if (idx > 0) doc.add(AreaBreak())
                val bytes = bitmapToBytes(bmp, options.compressImages)
                val image = Image(ImageDataFactory.create(bytes))
                image.setAutoScale(true)
                doc.add(image)
                if (options.embedOCR && idx < ocrText.size && ocrText[idx].isNotBlank()) {
                    doc.add(Paragraph("\nRecognized Text:").setBold().setFont(font))
                    doc.add(Paragraph(ocrText[idx]).setFont(font).setFontSize(10f))
                }
            }

            doc.close()

            validatePDFA(outputFile)

            Timber.i("PDF/A-2u created: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "PDF/A generation failed")
            false
        }
    }

    fun convertToPDFA(inputPdf: File, outputFile: File, options: PDFAOptions = PDFAOptions()): Boolean {
        return try {
            val reader = PdfReader(inputPdf)
            val icc = loadSRGB()
            val writerProps = WriterProperties().addXmpMetadata().setPdfVersion(PdfVersion.PDF_2_0)
            val pdfa = PdfADocument(outputFile.outputStream(), writerProps, PdfAConformanceLevel.PDF_A_2U, icc)
            pdfa.documentInfo.title = options.title
            pdfa.documentInfo.author = options.author
            pdfa.documentInfo.creator = options.creator
            val src = com.itextpdf.kernel.pdf.PdfDocument(reader)
            src.copyPagesTo(1, src.numberOfPages, pdfa)
            src.close()
            pdfa.close()
            validatePDFA(outputFile)
            true
        } catch (e: Exception) {
            Timber.e(e, "Convert to PDF/A failed")
            false
        }
    }

    private fun loadSRGB(): ICC_Profile {
        return try {
            context.assets.open("sRGB.icc").use { ICC_Profile.getInstance(it) }
        } catch (e: IOException) {
            ICC_Profile.getInstance(ICC_Profile.CS_sRGB)
        }
    }

    private fun loadEmbeddedFont(): PdfFont {
        return try {
            val bytes = context.assets.open("fonts/arial.ttf").use { it.readBytes() }
            PdfFontFactory.createFont(bytes, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
        } catch (e: IOException) {
            throw IllegalStateException("Embedded font missing in assets/fonts/arial.ttf", e)
        }
    }

    private fun bitmapToBytes(bmp: Bitmap, compress: Boolean): ByteArray {
        val baos = ByteArrayOutputStream()
        val format = if (compress) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        bmp.compress(format, if (compress) 85 else 100, baos)
        return baos.toByteArray()
    }

    private fun validatePDFA(file: File) {
        PdfADocument(PdfReader(file), PdfAConformanceLevel.PDF_A_2U).use { it.close() }
    }
}

