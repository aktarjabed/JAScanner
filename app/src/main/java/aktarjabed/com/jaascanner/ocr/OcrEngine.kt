package aktarjabed.com.jaascanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import aktarjabed.com.jaascanner.core.awaitResult
import aktarjabed.com.jaascanner.data.OcrLanguage
import aktarjabed.com.jaascanner.pdf.ImageDecoding
import aktarjabed.com.jaascanner.pdf.PdfPageRenderer
import aktarjabed.com.jaascanner.pdf.RecognisedLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The text of one page and where each line of it sits (fractions of the page, v downwards). */
data class OcrPage(val text: String, val lines: List<RecognisedLine>)

/** On-device text recognition (ML Kit). Latin and Devanagari scripts are supported. */
class OcrEngine {

    private fun createRecognizer(language: OcrLanguage): TextRecognizer = when (language) {
        OcrLanguage.Latin -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrLanguage.Devanagari -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun recognizeBitmap(bitmap: Bitmap, language: OcrLanguage): String {
        val recognizer = createRecognizer(language)
        try {
            return recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text
        } finally {
            recognizer.close()
        }
    }

    suspend fun recognizeImage(context: Context, uri: Uri, language: OcrLanguage): String {
        val bitmap = withContext(Dispatchers.IO) { ImageDecoding.decode(context, uri, maxSide = 2400) }
        try {
            return recognizeBitmap(bitmap, language)
        } finally {
            bitmap.recycle()
        }
    }

    /** Recognises the first [maxPages] pages of a PDF, returning one string per page. */
    suspend fun recognizePdf(
        file: File,
        language: OcrLanguage,
        maxPages: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> = recognizePdfPages(file, language, maxPages, onProgress).map { it.text }

    /** Like [recognizePdf], but also keeps the position of every line so a text layer can be embedded. */
    suspend fun recognizePdfPages(
        file: File,
        language: OcrLanguage,
        maxPages: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<OcrPage> {
        val renderer = PdfPageRenderer.open(file) ?: return emptyList()
        val recognizer = createRecognizer(language)
        try {
            val total = minOf(renderer.pageCount, maxPages)
            val pages = ArrayList<OcrPage>(total)
            for (index in 0 until total) {
                val bitmap = renderer.render(index, OCR_WIDTH_PX)
                if (bitmap == null) {
                    pages.add(OcrPage("", emptyList()))
                } else {
                    try {
                        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
                        val width = bitmap.width.toFloat()
                        val height = bitmap.height.toFloat()
                        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            RecognisedLine(
                                text = line.text,
                                u0 = (box.left / width).coerceIn(0f, 1f),
                                v0 = (box.top / height).coerceIn(0f, 1f),
                                u1 = (box.right / width).coerceIn(0f, 1f),
                                v1 = (box.bottom / height).coerceIn(0f, 1f),
                            )
                        }
                        pages.add(OcrPage(result.text, lines))
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, total)
            }
            return pages
        } finally {
            recognizer.close()
            withContext(NonCancellable) { renderer.release() }
        }
    }

    private companion object {
        const val OCR_WIDTH_PX = 1600
    }
}
