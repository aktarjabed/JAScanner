package aktarjabed.com.jaascanner.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
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
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val before = (pressed[0].previousPosition - pressed[1].previousPosition).getDistance()
                    val now = (pressed[0].position - pressed[1].position).getDistance()
                    if (before > 0f && now > 0f) latest(now / before)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
