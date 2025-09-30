package com.jascanner.editor

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentEditor @Inject constructor() {

    /**
     * Saves a new PDF with the specified page rotated.
     * @param inputFile The original PDF file.
     * @param outputFile The destination for the new, edited PDF file.
     * @param rotation The total rotation to apply (e.g., 90, 180, 270).
     * @param pageNum The 1-based page number to rotate.
     * @return True if successful, false otherwise.
     */
    fun saveRotation(inputFile: File, outputFile: File, rotation: Float, pageNum: Int): Boolean {
        return try {
            val pdfDoc = PdfDocument(PdfReader(inputFile), PdfWriter(outputFile))

            if (pageNum <= 0 || pageNum > pdfDoc.numberOfPages) {
                // Handle invalid page number
                pdfDoc.close()
                return false
            }

            val page = pdfDoc.getPage(pageNum)

            // Get current rotation of the page and add the new rotation.
            // The final value must be a multiple of 90.
            val currentRotation = page.rotation ?: 0
            val newRotation = (currentRotation + rotation.toInt()) % 360

            page.put(PdfName.Rotate, PdfNumber(newRotation))

            // This is crucial: If we don't do this, the new PDF will only have one page.
            // We need to ensure all pages from the source are copied to the destination.
            // The page we modified is already in the new document, so we don't need to copy it again.
            // A more efficient way for multi-page documents is to copy all pages first, then modify.
            // For now, let's keep it simple and assume we're just modifying the one page.
            // The default behavior of PdfDocument with reader and writer is to copy pages.

            pdfDoc.close()
            true
        } catch (e: Exception) {
            // Proper logging should be used here, e.g., Timber.e(e, "Failed to save rotation")
            e.printStackTrace()
            false
        }
    }
}