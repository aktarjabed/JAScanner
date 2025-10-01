package com.jascanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.jascanner.domain.model.EditableDocument
import com.jascanner.domain.model.EditablePage
import com.jascanner.presentation.editor.ExportSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private suspend fun addPageWithAnnotations(
        pdfDocument: PdfDocument,
        document: EditableDocument,
        page: EditablePage,
        settings: ExportSettings,
        font: com.itextpdf.kernel.font.PdfFont
    ) {
        try {
            val bitmap = page.processedBitmap ?: page.originalBitmap

            if (bitmap == null) {
                Timber.w("Skipping page ${page.pageId}: No bitmap available")
                return
            }

            if (bitmap.isRecycled) {
                Timber.w("Skipping page ${page.pageId}: Bitmap recycled")
                return
            }

            // Validate bitmap
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                Timber.w("Skipping page ${page.pageId}: Invalid dimensions")
                return
            }

            // Convert bitmap to byte array with error handling
            val stream = ByteArrayOutputStream()
            val compressed = bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                settings.compressionQuality.coerceIn(1, 100),
                stream
            )

            if (!compressed) {
                Timber.e("Failed to compress bitmap for page ${page.pageId}")
                return
            }

            val imageData = try {
                ImageDataFactory.create(stream.toByteArray())
            } catch (e: Exception) {
                Timber.e(e, "Failed to create image data for page ${page.pageId}")
                return
            }

            // Rest of implementation with try-catch blocks
            val pageSize = com.itextpdf.kernel.geom.PageSize(
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )

            val pdfPage = try {
                pdfDocument.addNewPage(pageSize)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add PDF page")
                return
            }

            // Continue with safe operations...

        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory adding page")
            System.gc()
        } catch (e: Exception) {
            Timber.e(e, "Error adding page to PDF")
        }
    }

    // Add asset existence check
    private fun checkRequiredAssets(): Boolean {
        val requiredAssets = listOf(
            "sRGB.icc",
            "fonts/arial.ttf"
        )

        return requiredAssets.all { assetPath ->
            try {
                context.assets.open(assetPath).use { true }
            } catch (e: Exception) {
                Timber.e("Missing required asset: $assetPath")
                false
            }
        }
    }
}