package com.jascanner.export

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor() {
    fun exportToPdf(text: String, outputFile: File): Boolean = try {
        val writer = PdfWriter(outputFile)
        val pdf = PdfDocument(writer)
        val doc = Document(pdf)
        doc.add(Paragraph(text))
        doc.close()
        true
    } catch (e: Exception) { Timber.e(e); false }
}

