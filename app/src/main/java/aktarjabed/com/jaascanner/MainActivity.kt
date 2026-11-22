package aktarjabed.com.jaascanner

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.JAScannerRoot
import kotlinx.coroutines.launch

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it for the app lock. */
class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as JAScannerApp).container) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only the first launch acts on the intent; a rotation must not re-import the same file.
        if (savedInstanceState == null) handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as JAScannerApp).container.settings.settings.collect { settings ->
                    if (settings.blockScreenshots) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent { JAScannerRoot(appViewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onActivityStopped()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) handlePdfUris(intent, listOf(uri)) else badIntent()
            }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                handlePdfUris(intent, listOfNotNull(uri))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                handlePdfUris(intent, uris)
            }
            ACTION_SCAN -> appViewModel.requestScan()
        }
    }

    /**
     * Collects PDFs from EXTRA_STREAM and, when a sender only fills ClipData, from there too.
     * Only content: URIs are accepted: a file: path from another app carries no grant we can rely
     * on, and the manifest no longer advertises it. The PDF header is checked again while copying.
     */
    private fun handlePdfUris(intent: Intent, extras: List<Uri>) {
        val clip: ClipData? = intent.clipData
        val fromClip = if (clip == null) {
            emptyList()
        } else {
            (0 until clip.itemCount).mapNotNull { index ->
                val uri = clip.getItemAt(index)?.uri
                val mime = try { clip.description.getMimeType(index) } catch (error: Exception) { null }
                if (uri != null && isPdfUri(uri, mime)) uri else null
            }
        }
        val pdfs = (extras.filter { isPdfUri(it, intent.type) } + fromClip).distinct()
        if (pdfs.isEmpty()) {
            badIntent()
        } else {
            appViewModel.openExternalPdfs(pdfs)
        }
    }

    private fun isPdfUri(uri: Uri, mime: String?): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && (mime == null || mime == "*/*" || mime == "application/pdf")

    private fun badIntent() {
        (application as JAScannerApp).container.messenger.post("That app did not send a readable PDF.")
    }

    companion object {
        const val ACTION_SCAN = "aktarjabed.com.jaascanner.action.SCAN"
    }
}
