package aktarjabed.com.jaascanner.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One-way channel from anywhere in the app to the snackbar at the root of the UI. */
class Messenger {
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun post(text: String) {
        mutableMessages.tryEmit(text)
    }
}
