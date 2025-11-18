package com.aktarjabed.jascanner.batch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.os.ParcelFileDescriptor
import com.aktarjabed.jascanner.enhancer.ImageEnhancer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class BatchScanManager(private val context: Context) {

    data class ScannedPage(
        val id: String = UUID.randomUUID().toString(),
        val originalFile: File,
        var enhancedFile: File? = null,
        var enhancementType: ImageEnhancer.EnhancementType = ImageEnhancer.EnhancementType.AUTO,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val pages = mutableListOf<ScannedPage>()
    private val listeners = mutableListOf<() -> Unit>()
    private val worker = Executors.newSingleThreadExecutor()

    /** Register a simple listener to refresh UI (RecyclerView adapter) */
    fun addOnChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeOnChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun getPages(): List<ScannedPage> = pages.toList()

    fun pageCount(): Int = pages.size

    /**
     * Add a captured image file to the batch.
     * Enhancement is queued (background thread). onComplete is invoked on Main when enhanced file is ready.
     */
    fun addPageAsync(originalFile: File, onComplete: ((ScannedPage) -> Unit)? = null) {
        val page = ScannedPage(originalFile = originalFile)
        pages.add(page)
        notifyListeners()

        worker.execute {
            try {
                // Load image efficiently
                val bitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath)

                // Enhance using ImageEnhancer (single pipeline)
                val enhancer = ImageEnhancer()
                val enhancedBitmap = enhancer.enhance(bitmap, page.enhancementType)

                // Save enhanced version
                val enhancedFile = File(context.cacheDir, "enhanced_${page.id}.jpg")
                saveBitmapToFile(enhancedBitmap, enhancedFile)

                page.enhancedFile = enhancedFile

                // Notify on main thread
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(page)
                    notifyListeners()
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun removePage(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        val p = pages.removeAt(idx)
        try {
            p.originalFile.delete()
            p.enhancedFile?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notifyListeners()
    }

    fun clearAll() {
        pages.forEach {
            try {
                it.originalFile.delete()
                it.enhancedFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        pages.clear()
        notifyListeners()
    }

    /**
     * Update enhancement for a page
     */
    suspend fun updatePageEnhancement(pageId: String, enhancementType: ImageEnhancer.EnhancementType): Boolean = withContext(Dispatchers.IO) {
        val position = pages.indexOfFirst { it.id == pageId }
        if (position == -1) return@withContext false

        val page = pages[position]

        // Load original bitmap
        val bitmap = android.graphics.BitmapFactory.decodeFile(page.originalFile.absolutePath)

        // Enhance using ImageEnhancer
        val enhancer = ImageEnhancer()
        val enhancedBitmap = enhancer.enhance(bitmap, enhancementType)

        // Delete old enhanced file
        page.enhancedFile?.delete()

        // Save new enhanced version
        val enhancedFile = File(context.cacheDir, "enhanced_${page.id}.jpg")
        saveBitmapToFile(enhancedBitmap, enhancedFile)

        page.enhancementType = enhancementType
        page.enhancedFile = enhancedFile

        // Notify on main thread
        withContext(Dispatchers.Main) {
            notifyListeners()
        }

        true
    }

    private fun notifyListeners() {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.invoke() }
        }
    }

    /**
     * Export all pages as a single PDF.
     * Uses PdfDocument for better compatibility and performance.
     */
    fun exportToPdf(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        return try {
            val document = PdfDocument()

            // Create a page for each scanned page
            for (page in pages) {
                val fileToUse = page.enhancedFile ?: page.originalFile
                val bitmap = android.graphics.BitmapFactory.decodeFile(fileToUse.absolutePath)

                // Create PDF page
                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width, bitmap.height, 1
                ).create()

                val pdfPage = document.startPage(pageInfo)

                // Draw bitmap to page
                val canvas = pdfPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                document.finishPage(pdfPage)
                bitmap.recycle()
            }

            // Write PDF to file
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
                document.close()
            }

            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    /**
     * Save bitmap to file
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}