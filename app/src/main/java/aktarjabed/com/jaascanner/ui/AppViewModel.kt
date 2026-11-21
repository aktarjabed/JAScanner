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
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.ui.nav.Screen
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
    private var stoppedAtMs = 0L

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

    fun openExternalPdf(uri: Uri) {
        viewModelScope.launch {
            val document = container.documents.importFromUri(uri, null, DocumentEntity.SOURCE_IMPORT)
            if (document == null) {
                container.messenger.post("That file is not a readable PDF.")
            } else {
                push(Screen.Viewer(document.id))
            }
        }
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

    fun onSettingsLoaded(appLockEnabled: Boolean) {
        lockEnabled = appLockEnabled
        if (!appLockEnabled) {
            lockState = LockState.Unlocked
        } else if (lockState == LockState.Checking) {
            lockState = LockState.Locked
        }
    }

    fun onActivityStopped() {
        stoppedAtMs = SystemClock.elapsedRealtime()
    }

    fun onActivityStarted() {
        val away = SystemClock.elapsedRealtime() - stoppedAtMs
        if (lockEnabled && lockState == LockState.Unlocked && stoppedAtMs != 0L && away > RELOCK_AFTER_MS) {
            lockState = LockState.Locked
        }
    }

    fun unlock() {
        lockState = LockState.Unlocked
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
