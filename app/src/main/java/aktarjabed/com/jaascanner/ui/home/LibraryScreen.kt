package aktarjabed.com.jaascanner.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.FolderPath
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.data.SortOrder
import aktarjabed.com.jaascanner.data.db.DocumentSummary
import aktarjabed.com.jaascanner.ui.components.ConfirmDialog
import aktarjabed.com.jaascanner.ui.components.DocThumbnail
import aktarjabed.com.jaascanner.ui.components.TextInputDialog

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    vm: LibraryViewModel,
    onOpen: (DocumentSummary) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newFolderDialog by remember { mutableStateOf(false) }
    var renameFolderDialog by remember { mutableStateOf(false) }
    var deleteFolderDialog by remember { mutableStateOf(false) }
    var subfolderDialog by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var inputNonce by rememberSaveable { mutableIntStateOf(0) }
    val selecting = state.selection.isNotEmpty()
    val activeFolder = (state.filter as? LibraryFilter.Folder)?.let { current -> state.folders.firstOrNull { it.id == current.id } }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            label = { Text("Search documents") },
            placeholder = { Text("Search names and scanned text") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.filter == LibraryFilter.All,
                    onClick = { vm.setFilter(LibraryFilter.All) },
                    label = { Text("All") },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == LibraryFilter.Favorites,
                    onClick = { vm.setFilter(LibraryFilter.Favorites) },
                    label = { Text("Favorites") },
                )
            }
            items(state.folders, key = { it.id }) { folder ->
                FilterChip(
                    selected = (state.filter as? LibraryFilter.Folder)?.id == folder.id,
                    onClick = { vm.setFilter(LibraryFilter.Folder(folder.id)) },
                    label = { Text(FolderPath.label(state.folders, folder.id)) },
                )
            }
            item {
                AssistChip(
                    onClick = { inputNonce++; newFolderDialog = true },
                    label = { Text("+ Folder") },
                )
            }
        }

        if (activeFolder != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Folder: ${FolderPath.label(state.folders, activeFolder.id)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { inputNonce++; subfolderDialog = true }) { Text("+ Subfolder") }
                TextButton(onClick = { inputNonce++; renameFolderDialog = true }) { Text("Rename") }
                TextButton(onClick = { deleteFolderDialog = true }) { Text("Delete folder") }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.documents.size == 1) "1 document" else "${state.documents.size} documents",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { sortMenu = true }) { Text(state.settings.sortOrder.label) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                vm.setSortOrder(order)
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            TextButton(onClick = { vm.setGridView(!state.settings.gridView) }) {
                Text(if (state.settings.gridView) "List" else "Grid")
            }
        }

        if (state.loaded && state.loadFailed == null && state.searchFailed != null && state.documents.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Text search failed: ${state.searchFailed}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::retrySearch) { Text("Retry") }
            }
        }

        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadFailed != null -> ErrorPanel(
                message = "Could not load the library: ${state.loadFailed}",
                onRetry = vm::retryLoad,
            )
            state.documents.isEmpty() && state.searchFailed != null -> ErrorPanel(
                message = "Text search failed: ${state.searchFailed}",
                onRetry = vm::retrySearch,
            )
            state.documents.isEmpty() && state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.documents.isEmpty() -> EmptyLibrary(
                searching = state.query.isNotBlank() || state.filter != LibraryFilter.All,
                hasAny = state.totalCount > 0,
                onImport = onImport,
            )
            state.settings.gridView -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.documents, key = { it.id }) { doc ->
                    GridCard(
                        doc = doc,
                        selected = doc.id in state.selection,
                        onClick = { if (selecting) vm.toggleSelect(doc.id) else onOpen(doc) },
                        onLongClick = { vm.toggleSelect(doc.id) },
                        onClickLabel = if (selecting) "Toggle selection" else "Open document",
                        onLongClickLabel = "Select document",
                        onFavorite = { vm.toggleFavorite(doc.id, doc.favorite) },
                    )
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.documents, key = { it.id }) { doc ->
                    ListRow(
                        doc = doc,
                        selected = doc.id in state.selection,
                        onClick = { if (selecting) vm.toggleSelect(doc.id) else onOpen(doc) },
                        onLongClick = { vm.toggleSelect(doc.id) },
                        onClickLabel = if (selecting) "Toggle selection" else "Open document",
                        onLongClickLabel = "Select document",
                        onFavorite = { vm.toggleFavorite(doc.id, doc.favorite) },
                    )
                }
            }
        }
    }

    if (newFolderDialog) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmLabel = "Create",
            dialogKey = Pair("newFolder", inputNonce),
            onConfirm = {
                vm.createFolder(it)
                newFolderDialog = false
            },
            onDismiss = { newFolderDialog = false },
        )
    }
    if (subfolderDialog && activeFolder != null) {
        TextInputDialog(
            title = "New subfolder in ${activeFolder.name}",
            label = "Subfolder name",
            initial = "",
            confirmLabel = "Create",
            dialogKey = Pair("subfolder-${activeFolder.id}", inputNonce),
            onConfirm = {
                vm.createFolder(it, parentId = activeFolder.id)
                subfolderDialog = false
            },
            onDismiss = { subfolderDialog = false },
        )
    }
    if (renameFolderDialog && activeFolder != null) {
        TextInputDialog(
            title = "Rename folder",
            label = "Folder name",
            initial = activeFolder.name,
            confirmLabel = "Rename",
            dialogKey = Pair("renameFolder-${activeFolder.id}", inputNonce),
            onConfirm = {
                vm.renameFolder(activeFolder.id, it)
                renameFolderDialog = false
            },
            onDismiss = { renameFolderDialog = false },
        )
    }
    if (deleteFolderDialog && activeFolder != null) {
        ConfirmDialog(
            title = "Delete folder?",
            message = "Documents directly in \"${activeFolder.name}\" move to All. Subfolders keep their documents and become top-level folders.",
            confirmLabel = "Delete folder",
            onConfirm = {
                vm.deleteFolder(activeFolder.id)
                deleteFolderDialog = false
            },
            onDismiss = { deleteFolderDialog = false },
        )
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyLibrary(searching: Boolean, hasAny: Boolean, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (searching && hasAny) {
            Text("Nothing matches", style = MaterialTheme.typography.titleMedium)
            Text(
                "Try a different word, or switch the filter above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("No documents yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap Scan to capture a document, or import a PDF you already have.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onImport) { Text("Import a PDF") }
        }
    }
}

private fun metaOf(doc: DocumentSummary): String {
    val pages = when (doc.pageCount) {
        0 -> "Locked or unreadable"
        1 -> "1 page"
        else -> "${doc.pageCount} pages"
    }
    val base = "$pages · ${Formatters.fileSize(doc.sizeBytes)}"
    return if (doc.pinned) "Pinned · $base" else base
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCard(
    doc: DocumentSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickLabel: String,
    onLongClickLabel: String,
    onFavorite: () -> Unit,
) {
    val file = LocalContext.current.container.documents.fileOf(doc)
    Card(
        modifier = Modifier.combinedClickable(
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            DocThumbnail(file, doc.updatedAt, Modifier.fillMaxWidth().aspectRatio(0.75f))
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp),
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (doc.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (doc.favorite) "Remove from favorites" else "Add to favorites",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(metaOf(doc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Formatters.dateTime(doc.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(
    doc: DocumentSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickLabel: String,
    onLongClickLabel: String,
    onFavorite: () -> Unit,
) {
    val file = LocalContext.current.container.documents.fileOf(doc)
    ListItem(
        headlineContent = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(metaOf(doc) + " · " + Formatters.dateTime(doc.updatedAt), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Box {
                DocThumbnail(file, doc.updatedAt, Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)), widthPx = 200)
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onFavorite) {
                Icon(
                    if (doc.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (doc.favorite) "Remove from favorites" else "Add to favorites",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.combinedClickable(
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    )
}
