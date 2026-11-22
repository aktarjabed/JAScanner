package aktarjabed.com.jaascanner.ui

import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.data.AppSettings
import aktarjabed.com.jaascanner.data.DocumentRepository.ImportTooLargeException
import aktarjabed.com.jaascanner.data.LockGrace
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.ui.nav.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One of these per screen on the back stack, so a screen's ViewModels die when it is popped. */
class ScreenOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

enum class LockState { Checking, Locked, Unlocked }

/** Holds the back stack, the app-lock state and requests that arrive from outside (intents). */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val stack = mutableStateListOf<Screen>(Screen.Home)

    var lockState by mutableStateOf(LockState.Checking)
        private set

    var pendingScan by mutableStateOf(false)
        private set

    /** A page the viewer should scroll to the next time it shows [first] (set by the page organiser). */
    var pendingJump by mutableStateOf<Pair<Long, Int>?>(null)
        private set

    private val owners = HashMap<Long, ScreenOwner>()
    private var lockEnabled = false
    private var lockGraceMs = LockGrace.Seconds30.milliseconds
    private var stoppedAtMs = 0L

    /**
     * PDFs handed to us by another app while JAScanner is locked or still checking settings.
     * A ViewModel survives rotation, so the URIs are held here rather than in the Activity. Nothing
     * is copied into the library until [lockState] is [LockState.Unlocked].
     */
    private val pendingExternalUris = mutableListOf<Uri>()

    fun ownerFor(screen: Screen): ScreenOwner = owners.getOrPut(screen.id) { ScreenOwner() }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.size <= 1) return
        val removed = stack.removeAt(stack.lastIndex)
        owners.remove(removed.id)?.viewModelStore?.clear()
    }

    /** Pops the current screen and pushes [screen] in its place. */
    fun replaceTop(screen: Screen) {
        pop()
        push(screen)
    }

    /**
     * Replaces the top screen with [screen], but only when [originId] is still on top.
     *
     * App-scoped save callbacks finish after the user may have left the screen that started the
     * save. Without this check, [replaceTop] would pop whatever is now on top (the Library or a
     * different document) and destroy it. When the origin is gone, the saved document is still
     * registered and announced, so the user can open it from the Library.
     */
    fun replaceTopIfCurrent(originId: Long, screen: Screen) {
        if (stack.lastOrNull()?.id != originId) return
        replaceTop(screen)
    }

    fun popToHome() {
        while (stack.size > 1) pop()
    }

    // --- Requests from outside the app -----------------------------------------------------

    fun openExternalPdf(uri: Uri) = openExternalPdfs(listOf(uri))

    /** Queues everything the share sheet or a file manager handed us until the app is unlocked. */
    fun openExternalPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (lockState != LockState.Unlocked) {
            pendingExternalUris.addAll(uris)
            return
        }
        importExternal(uris)
    }

    private fun importExternal(uris: List<Uri>) {
        viewModelScope.launch {
            var imported = 0
            var unreadable = 0
            var tooLarge = 0
            var firstImportedId: Long? = null
            for (uri in uris) {
                try {
                    val document = container.documents.importFromUri(uri, null, DocumentEntity.SOURCE_IMPORT)
                    if (document == null) {
                        unreadable++
                    } else {
                        imported++
                        if (firstImportedId == null) firstImportedId = document.id
                        if (container.settings.settings.first().autoOcr) {
                            container.ocrIndexer.indexInBackground(document.id)
                        }
                    }
                } catch (error: ImportTooLargeException) {
                    tooLarge++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    unreadable++
                }
            }
            val parts = mutableListOf<String>()
            if (imported == 1) parts += "Imported 1 PDF."
            if (imported > 1) parts += "Imported $imported PDFs."
            if (unreadable > 0) parts += "$unreadable file(s) could not be read as PDFs."
            if (tooLarge > 0) parts += "$tooLarge file(s) were over the 200 MB import limit."
            if (parts.isNotEmpty()) container.messenger.post(parts.joinToString(" "))
            if (imported == 1) firstImportedId?.let { push(Screen.Viewer(it)) }
        }
    }

    /** Imports anything that arrived while locked, once the lock is gone. */
    private fun drainPendingImports() {
        if (lockState != LockState.Unlocked || pendingExternalUris.isEmpty()) return
        val queued = pendingExternalUris.toList()
        pendingExternalUris.clear()
        importExternal(queued)
    }

    fun requestJump(documentId: Long, page: Int) {
        pendingJump = documentId to page
    }

    fun clearJump() {
        pendingJump = null
    }

    fun requestScan() {
        pendingScan = true
    }

    fun consumeScanRequest() {
        pendingScan = false
    }

    // --- App lock --------------------------------------------------------------------------

    fun onSettingsLoaded(settings: AppSettings) {
        lockEnabled = settings.appLock
        lockGraceMs = settings.lockGrace.milliseconds
        if (!lockEnabled) {
            lockState = LockState.Unlocked
        } else if (lockState == LockState.Checking) {
            lockState = LockState.Locked
        }
        drainPendingImports()
    }

    fun onActivityStopped() {
        stoppedAtMs = SystemClock.elapsedRealtime()
        // "Immediately" has no grace window: lock as soon as the app leaves the foreground.
        if (lockEnabled && lockGraceMs == 0L) lockState = LockState.Locked
    }

    fun onActivityStarted() {
        val away = SystemClock.elapsedRealtime() - stoppedAtMs
        if (lockEnabled && lockState == LockState.Unlocked && stoppedAtMs != 0L && lockGraceMs > 0L && away >= lockGraceMs) {
            lockState = LockState.Locked
        }
    }

    fun unlock() {
        lockState = LockState.Unlocked
        drainPendingImports()
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}
