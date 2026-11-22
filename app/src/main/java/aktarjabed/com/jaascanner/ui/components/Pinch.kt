package aktarjabed.com.jaascanner.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onZoom] with the change in finger distance (above 1 means zoom in) while two or more fingers
 * are down. One-finger gestures are left alone, so scrolling and drawing keep working.
 */
@Composable
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier {
    val latest by rememberUpdatedState(onZoom)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Track the same two fingers for the whole gesture. Using changes[0] and changes[1]
            // would switch fingers when one is lifted or when the event order changes, producing
            // a jump in the zoom factor.
            var tracked: List<PointerId> = emptyList()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val stillDown = tracked.size == 2 && tracked.all { id -> pressed.any { it.id == id } }
                    if (!stillDown) {
                        tracked = pressed.take(2).map { it.id }
                        // Consume only the fingers this gesture owns; a third finger and any
                        // one-finger drag elsewhere are left to their own handlers.
                        pressed.take(2).forEach { it.consume() }
                    } else {
                        val first = pressed.first { it.id == tracked[0] }
                        val second = pressed.first { it.id == tracked[1] }
                        val before = (first.previousPosition - second.previousPosition).getDistance()
                        val now = (first.position - second.position).getDistance()
                        if (before > 0f && now > 0f) latest(now / before)
                        first.consume()
                        second.consume()
                    }
                } else {
                    tracked = emptyList()
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
