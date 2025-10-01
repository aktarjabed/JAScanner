package com.jascanner.presentation.editor.tools

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.consumePositionChange

suspend fun PointerInputScope.detectDragGesturesSafe(
    onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit
) {
    detectDragGestures(
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDrag = { change, dragAmount ->
            change.consume() // CRITICAL: Consume the event
            onDrag(change, dragAmount)
        }
    )
}

suspend fun PointerInputScope.detectTapGesturesSafe(
    onTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onDoubleTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onLongPress: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
) {
    detectTapGestures(
        onTap = onTap,
        onDoubleTap = onDoubleTap,
        onLongPress = onLongPress
    )
}