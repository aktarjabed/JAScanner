package aktarjabed.com.jaascanner.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import aktarjabed.com.jaascanner.core.awaitResult
import aktarjabed.com.jaascanner.data.AppSettings
import aktarjabed.com.jaascanner.data.ScanMode
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

data class ScanResult(val pdfUri: Uri, val pageCount: Int)

/** Most pages one scan session may ask for. The Google scanner may apply its own lower limit on some devices. */
const val MAX_PAGES = 500

/**
 * Wraps the Google Play services document scanner: live edge detection, auto-crop,
 * perspective fix, clean-up filters and multi-page capture, all on device.
 */
object ScannerLauncher {

    private fun optionsFor(settings: AppSettings): GmsDocumentScannerOptions {
        val mode = when (settings.scanMode) {
            ScanMode.Full -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
            ScanMode.BaseWithFilter -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
            ScanMode.Base -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
        }
        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(settings.pageLimit.coerceIn(1, MAX_PAGES))
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(mode)
            .build()
    }

    /** Resolves the intent to pass to a StartIntentSenderForResult launcher. */
    suspend fun startIntentSender(activity: Activity, settings: AppSettings): IntentSender =
        GmsDocumentScanning.getClient(optionsFor(settings)).getStartScanIntent(activity).awaitResult()

    /** One card side: a single cropped photo (camera or gallery), returned as a JPEG. */
    suspend fun startCardIntentSender(activity: Activity): IntentSender {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        return GmsDocumentScanning.getClient(options).getStartScanIntent(activity).awaitResult()
    }

    fun parseCardResult(data: Intent?): Uri? =
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri

    fun parseResult(data: Intent?): ScanResult? {
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return null
        val pdf = result.pdf ?: return null
        return ScanResult(pdf.uri, pdf.pageCount)
    }
}
