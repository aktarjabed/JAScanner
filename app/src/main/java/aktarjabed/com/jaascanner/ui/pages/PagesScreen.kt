package aktarjabed.com.jaascanner.ui.pages

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.core.PageEntry
import aktarjabed.com.jaascanner.pdf.PdfPageSource
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.ConfirmDialog
import aktarjabed.com.jaascanner.ui.components.TextInputDialog
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import kotlinx.coroutines.launch

private const val THUMB_PX = 260
private const val EDGE_SCROLL_ZONE_PX = 90f
private const val EDGE_SCROLL_STEP_PX = 36f

/**
 * Page thumbnails of one document. Tap pages to select them; press and hold a page, then drag it
 * to a new place. Rotate, delete, duplicate or move the selection from the bar at the bottom, then
 * Save writes a new copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(app: AppViewModel, documentId: Long, screenId: Long) {
    val vm = containerViewModel { PagesViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val source = state.source
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var discardDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<Int?>(null) }

    val leave: () -> Unit = { if (state.changed) discardDialog = true else app.pop() }
    BackHandler(onBack = leave)

    val hasSelection = state.selected.isNotEmpty()
    val allSelected = state.entries.isNotEmpty() && state.selected.size == state.entries.size

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Column {
                        Text("Pages", maxLines = 1)
                        Text(
                            if (hasSelection) "${state.entries.size} pages, ${state.selected.size} selected" else "${state.entries.size} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.selected.size == 1) {
                        TextButton(onClick = {
                            val entry = state.entries.firstOrNull { it.id in state.selected }
                            if (entry != null) {
                                app.requestJump(documentId, entry.source)
                                app.pop()
                            }
                        }) { Text("Open") }
                    }
                    TextButton(onClick = { if (allSelected) vm.clearSelection() else vm.selectAll() }, enabled = state.entries.isNotEmpty()) {
                        Text(if (allSelected) "None" else "All")
                    }
                    TextButton(
                        onClick = { vm.save { newId -> app.replaceTopIfCurrent(screenId, Screen.Viewer(newId)) } },
                        enabled = state.changed && !state.saving,
                    ) { Text("Save") }
                },
            )
        },
        bottomBar = {
            BottomAppBar(contentPadding = PaddingValues(horizontal = 4.dp)) {
                BarAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier", hasSelection) { vm.shift(-1) }
                BarAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later", hasSelection) { vm.shift(1) }
                BarAction(Icons.AutoMirrored.Filled.ArrowForward, "Move to", hasSelection) { moveDialog = true }
                BarAction(Icons.Filled.Refresh, "Rotate", hasSelection) { vm.rotateSelected(90) }
                BarAction(Icons.Filled.Add, "Copy", hasSelection) { vm.duplicateSelected() }
                BarAction(Icons.Filled.Delete, "Delete", hasSelection) { vm.deleteSelected() }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Tap pages to select. Press and hold a page, then drag it to reorder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    source == null -> Text(
                        "This PDF cannot be opened. It may be password-protected or damaged.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { offset ->
                                    val entries = vm.state.value.entries
                                    val index = indexAt(gridState, offset)
                                    val entry = if (index != null) entries.getOrNull(index) else null
                                    if (entry != null) vm.toggle(entry.id)
                                })
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val entries = vm.state.value.entries
                                        val index = indexAt(gridState, offset)
                                        draggingId = if (index != null) entries.getOrNull(index)?.id else null
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val id = draggingId
                                        if (id != null) {
                                            val over = indexAt(gridState, change.position)
                                            val from = vm.indexOfId(id)
                                            if (over != null && from >= 0 && over != from) vm.move(from, over)
                                            if (change.position.y < EDGE_SCROLL_ZONE_PX) {
                                                scope.launch { gridState.scrollBy(-EDGE_SCROLL_STEP_PX) }
                                            } else if (change.position.y > size.height - EDGE_SCROLL_ZONE_PX) {
                                                scope.launch { gridState.scrollBy(EDGE_SCROLL_STEP_PX) }
                                            }
                                        }
                                    },
                                    onDragEnd = { draggingId = null },
                                    onDragCancel = { draggingId = null },
                                )
                            },
                    ) {
                        itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                            PageCell(
                                source = source,
                                entry = entry,
                                position = index + 1,
                                selected = entry.id in state.selected,
                                dragging = entry.id == draggingId,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
                if (state.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (moveDialog) {
        TextInputDialog(
            title = "Move to position",
            label = "Position (1-${state.entries.size})",
            initial = "",
            confirmLabel = "Move",
            onConfirm = { text ->
                val position = text.toIntOrNull()
                if (position != null && position in 1..state.entries.size) vm.moveSelectedTo(position)
                moveDialog = false
            },
            onDismiss = { moveDialog = false },
        )
    }
    if (discardDialog) {
        ConfirmDialog(
            title = "Discard your changes?",
            message = "The new page order has not been saved.",
            confirmLabel = "Discard",
            onConfirm = {
                discardDialog = false
                app.pop()
            },
            onDismiss = { discardDialog = false },
        )
    }
}

/** Index of the grid item under [position] (relative to the grid), or null between items. */
private fun indexAt(grid: LazyGridState, position: Offset): Int? =
    grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        position.x >= info.offset.x && position.x < info.offset.x + info.size.width &&
            position.y >= info.offset.y && position.y < info.offset.y + info.size.height
    }?.index

@Composable
private fun PageCell(
    source: PdfPageSource,
    entry: PageEntry,
    position: Int,
    selected: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, source, entry.source, entry.rotation) {
        val base = source.render(entry.source, THUMB_PX)
        value = if (base == null || entry.rotation == 0) {
            base
        } else {
            val turn = Matrix()
            turn.postRotate(entry.rotation.toFloat())
            Bitmap.createBitmap(base, 0, 0, base.width, base.height, turn, true)
        }
    }
    val heightOverWidth = source.heightOverWidth(entry.source)
    val aspect = if (entry.rotation % 180 == 0) 1f / heightOverWidth else heightOverWidth
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val label = buildString {
        append(position)
        if (entry.source + 1 != position) append(" (was ${entry.source + 1})")
        if (entry.rotation != 0) append(", turned ${entry.rotation}°")
    }
    Column(
        modifier.graphicsLayer {
            val scale = if (dragging) 1.07f else 1f
            scaleX = scale
            scaleY = scale
            alpha = if (dragging) 0.85f else 1f
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.3f, 3f))
                .background(Color.White)
                .border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp)),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page $position",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RowScope.BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
    Column(
        Modifier
            .weight(1f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
