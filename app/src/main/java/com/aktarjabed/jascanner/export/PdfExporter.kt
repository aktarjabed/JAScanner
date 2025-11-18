package com.aktarjabed.jascanner.export

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportFilesAsPdf(imageFiles: List<File>, output: File) {
        val document = PdfDocument()
        try {
            imageFiles.forEachIndexed { index, file ->
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawBitmap(bmp, 0f, 0f, null)
                document.finishPage(page)
                bmp.recycle()
            }

            FileOutputStream(output).use { fos ->
                document.writeTo(fos)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PDF export failed", t)
            throw t
        } finally {
            document.close()
        }
    }

    private const val TAG = "PdfExporter"
}