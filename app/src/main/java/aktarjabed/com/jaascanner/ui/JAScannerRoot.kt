package aktarjabed.com.jaascanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import aktarjabed.com.jaascanner.container
import kotlinx.coroutines.CancellationException
import aktarjabed.com.jaascanner.data.AppSettings
import aktarjabed.com.jaascanner.data.ThemeMode
import aktarjabed.com.jaascanner.ui.cards.CardScanScreen
import aktarjabed.com.jaascanner.ui.editor.EditorScreen
import aktarjabed.com.jaascanner.ui.form.FormScreen
import aktarjabed.com.jaascanner.ui.home.HomeScreen
import aktarjabed.com.jaascanner.ui.lock.LockScreen
import aktarjabed.com.jaascanner.ui.nav.Screen
import aktarjabed.com.jaascanner.ui.ocr.OcrScreen
import aktarjabed.com.jaascanner.ui.pages.PagesScreen
import aktarjabed.com.jaascanner.ui.reader.ReaderScreen
import aktarjabed.com.jaascanner.ui.qr.QrScreen
import aktarjabed.com.jaascanner.ui.theme.JAScannerTheme
import aktarjabed.com.jaascanner.ui.tools.ToolScreen
import aktarjabed.com.jaascanner.ui.viewer.ViewerScreen

@Composable
fun JAScannerRoot(app: AppViewModel) {
    val container = LocalContext.current.container
    val settings by produceState<AppSettings?>(initialValue = null) {
        try {
            container.settings.settings.collect { value = it }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Never leave the app on the empty Checking screen: fall back to defaults.
            value = AppSettings()
            container.messenger.post("Settings could not be loaded. Defaults are being used.")
        }
    }

    LaunchedEffect(settings?.appLock) {
        settings?.let { app.onSettingsLoaded(it.appLock) }
    }

    JAScannerTheme(settings?.themeMode ?: ThemeMode.System, dynamicColor = settings?.useDynamicColor ?: false) {
        Surface(Modifier.fillMaxSize()) {
            val snackbarHost = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                container.messenger.messages.collect { text -> snackbarHost.showSnackbar(text) }
            }
            Box(Modifier.fillMaxSize()) {
                when (app.lockState) {
                    LockState.Checking -> Unit
                    LockState.Locked -> LockScreen(app)
                    LockState.Unlocked -> Navigation(app)
                }
                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
                )
            }
        }
    }
}

@Composable
private fun Navigation(app: AppViewModel) {
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = app.stack.size > 1) { app.pop() }

    val screen = app.stack.last()
    val owner = app.ownerFor(screen)
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        key(screen.id) {
            holder.SaveableStateProvider(screen.id) {
                when (screen) {
                    Screen.Home -> HomeScreen(app)
                    is Screen.Viewer -> ViewerScreen(app, screen.documentId)
                    is Screen.Editor -> EditorScreen(app, screen.documentId, screen.id)
                    is Screen.Ocr -> OcrScreen(app, screen.documentId)
                    is Screen.Tool -> ToolScreen(app, screen.kind, screen.preselectedIds)
                    is Screen.Qr -> QrScreen(app)
                    is Screen.Cards -> CardScanScreen(app, screen.id)
                    is Screen.Form -> FormScreen(app, screen.documentId)
                    is Screen.Pages -> PagesScreen(app, screen.documentId, screen.id)
                    is Screen.Reader -> ReaderScreen(app, screen.documentId)
                }
            }
        }
    }
}
