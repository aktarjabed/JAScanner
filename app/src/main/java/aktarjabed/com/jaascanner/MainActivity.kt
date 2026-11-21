package aktarjabed.com.jaascanner

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
            Intent.ACTION_VIEW -> intent.data?.let(appViewModel::openExternalPdf)
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) appViewModel.openExternalPdf(uri)
            }
            ACTION_SCAN -> appViewModel.requestScan()
        }
    }

    companion object {
        const val ACTION_SCAN = "aktarjabed.com.jaascanner.action.SCAN"
    }
}
