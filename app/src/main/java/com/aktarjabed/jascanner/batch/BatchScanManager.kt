package com.aktarjabed.jascanner.batch

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.aktarjabed.jascanner.enhancer.ImageEnhancer
import com.aktarjabed.jascanner.export.PdfExporter
import com.aktarjabed.jascanner.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val enhancer = ImageEnhancer()

    fun addOnChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeOnChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun getPages(): List<ScannedPage> = pages.toList()
    fun pageCount(): Int = pages.size

    fun addPageAsync(originalFile: File, onComplete: ((ScannedPage) -> Unit)? = null) {
        val page = ScannedPage(originalFile = originalFile)
        pages.add(page)
        notifyListeners()

        worker.execute {
            try {
                val bmp = BitmapFactory.decodeFile(originalFile.absolutePath)
                val enhancedBitmap = enhancer.enhance(bmp, page.enhancementType)
                val outFile = File(context.cacheDir, "enh_${page.id}.jpg")
                FileUtils.saveBitmapToFile(enhancedBitmap, outFile)
                page.enhancedFile = outFile

                mainHandler.post {
                    onComplete?.invoke(page)
                    notifyListeners()
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    suspend fun updatePageEnhancement(pageId: String, enhancementType: ImageEnhancer.EnhancementType): Boolean {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return false
        val page = pages[idx]

        return try {
            val bmp = BitmapFactory.decodeFile(page.originalFile.absolutePath)
            val enhancedBitmap = enhancer.enhance(bmp, enhancementType)
            page.enhancedFile?.delete()
            val outFile = File(context.cacheDir, "enh_${page.id}.jpg")
            FileUtils.saveBitmapToFile(enhancedBitmap, outFile)
            page.enhancedFile = outFile
            page.enhancementType = enhancementType
            notifyListeners()
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    fun removePage(pageId: String) {
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return
        val removed = pages.removeAt(idx)
        removed.enhancedFile?.delete()
        notifyListeners()
    }

    fun clearAll() {
        pages.forEach {
            try {
                it.enhancedFile?.delete()
            } catch (e: Exception) { e.printStackTrace() }
        }
        pages.clear()
        notifyListeners()
    }

    fun exportToPdf(outputFile: File): Boolean {
        return try {
            val files = pages.map { it.enhancedFile ?: it.originalFile }
            PdfExporter.exportFilesAsPdf(files, outputFile)
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    private fun notifyListeners() {
        CoroutineScope(Dispatchers.Main).launch {
            listeners.forEach { it.invoke() }
        }
    }
}