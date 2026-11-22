package aktarjabed.com.jaascanner.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-way channel from anywhere in the app to the snackbar at the root of the UI.
 *
 * The buffer is a safety net for bursts (several imports finishing at once), not a queue the
 * user is expected to read in full. When it is full, the oldest message is dropped so the
 * newest, usually most relevant, one still arrives; silently dropping the new message would
 * leave the user looking at stale feedback.
 */
class Messenger {
    private val mutableMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun post(text: String) {
        mutableMessages.tryEmit(text)
    }
}
