package aktarjabed.com.jaascanner.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.pdf.Bookmark
import aktarjabed.com.jaascanner.pdf.NoteInfo
import aktarjabed.com.jaascanner.pdf.PdfFilePrintAdapter
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.ConfirmDialog
import aktarjabed.com.jaascanner.ui.components.PageView
import aktarjabed.com.jaascanner.ui.components.TextInputDialog
import aktarjabed.com.jaascanner.ui.components.pinchToZoom
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import aktarjabed.com.jaascanner.ui.nav.ToolKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(app: AppViewModel, documentId: Long) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { ViewerViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val document = state.document
    val source = state.source

    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var night by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var gotoDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var marksDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val shareNow: () -> Unit = {
        val file = vm.file()
        if (file != null && document != null) {
            scope.launch {
                try {
                    ShareHelper.sharePdf(context, file, document.name)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    container.messenger.post(error.toUserMessage())
                }
            }
        }
    }

    // Coming back from the page organiser with a page to show.
    LaunchedEffect(app.pendingJump, source) {
        val jump = app.pendingJump
        if (jump != null && jump.first == documentId && source != null) {
            listState.scrollToItem(jump.second.coerceIn(0, source.pageCount - 1))
            app.clearJump()
        }
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        vm.clearSearch()
    }

    LaunchedEffect(state.searchMatches, state.searchIndex) {
        val page = state.searchMatches.getOrNull(state.searchIndex)
        if (page != null) listState.scrollToItem(page)
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = vm.saveCopyTo(context, uri)
                container.messenger.post(if (saved) "Copy saved." else "Could not save the copy.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(document?.name ?: "Document", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (source != null) {
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) vm.clearSearch()
                        }) { Icon(Icons.Filled.Search, contentDescription = "Search in document") }
                    }
                    if (document != null) {
                        IconButton(onClick = shareNow) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                MenuItem("Save a copy") {
                                    menu = false
                                    saveLauncher.launch(Formatters.safeFileName(document.name) + ".pdf")
                                }
                                if (source != null) {
                                    MenuItem("Print") {
                                        menu = false
                                        val activity = context.findActivity()
                                        val file = vm.file()
                                        if (activity != null && file != null) {
                                            PdfFilePrintAdapter.print(activity, file, document.name, document.pageCount)
                                        }
                                    }
                                    MenuItem("Go to page") {
                                        menu = false
                                        gotoDialog = true
                                    }
                                    MenuItem(if (night) "Night mode: on" else "Night mode: off") {
                                        menu = false
                                        night = !night
                                    }
                                    MenuItem("Export pages as images") {
                                        menu = false
                                        app.push(Screen.Tool(ToolKind.ExportImages, listOf(documentId)))
                                    }
                                }
                                MenuItem("Fill in form") {
                                    menu = false
                                    app.push(Screen.Form(documentId))
                                }
                                MenuItem("Add password") {
                                    menu = false
                                    app.push(Screen.Tool(ToolKind.Protect, listOf(documentId)))
                                }
                                MenuItem(if (document.favorite) "Remove from favorites" else "Add to favorites") {
                                    menu = false
                                    vm.toggleFavorite()
                                }
                                MenuItem("Rename") {
                                    menu = false
                                    renameDialog = true
                                }
                                MenuItem("Delete") {
                                    menu = false
                                    deleteDialog = true
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (source != null && document != null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = false,
                        onClick = { app.push(Screen.Pages(documentId)) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Pages") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            vm.loadPanel()
                            marksDialog = true
                        },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        label = { Text("Marks") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { app.push(Screen.Editor(documentId)) },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        label = { Text("Edit") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { app.push(Screen.Ocr(documentId)) },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                        label = { Text("Text") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { app.push(Screen.Reader(documentId)) },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        label = { Text("Read") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searchOpen && source != null) {
                SearchBar(
                    query = state.searchQuery,
                    searching = state.searching,
                    matchCount = state.searchMatches.size,
                    matchIndex = state.searchIndex,
                    noTextLayer = state.hasTextLayer == false,
                    onQuery = vm::search,
                    onPrevious = { vm.stepMatch(-1) },
                    onNext = { vm.stepMatch(1) },
                    onOpenOcr = { app.push(Screen.Ocr(documentId)) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(if (night) Color.Black else MaterialTheme.colorScheme.surfaceVariant)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    source == null || document == null -> UnreadableMessage(
                        canUnlock = document != null,
                        onUnlock = { app.replaceTop(Screen.Tool(ToolKind.Unlock, listOf(documentId))) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> {
                        BoxWithConstraints(
                            Modifier.fillMaxSize().pinchToZoom { factor -> zoom = (zoom * factor).coerceIn(1f, 4f) },
                        ) {
                            val pageWidth = maxWidth * zoom
                            val horizontal = rememberScrollState()
                            Box(Modifier.fillMaxHeight().horizontalScroll(horizontal)) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxHeight().width(pageWidth),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(count = source.pageCount, key = { it }) { index ->
                                        PageView(source = source, index = index, width = pageWidth, night = night)
                                    }
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            tonalElevation = 4.dp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) },
                                    modifier = Modifier.semantics { contentDescription = "Zoom out" },
                                ) { Text("\u2212") }
                                Text(
                                    "Page ${listState.firstVisibleItemIndex + 1} / ${source.pageCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                TextButton(
                                    onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f) },
                                    modifier = Modifier.semantics { contentDescription = "Zoom in" },
                                ) { Text("+") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (marksDialog && source != null) {
        MarksDialog(
            bookmarks = state.bookmarks,
            notes = state.notes,
            loading = state.panelLoading,
            busy = state.panelBusy,
            currentPage = listState.firstVisibleItemIndex,
            onJump = { page ->
                marksDialog = false
                scope.launch { listState.scrollToItem(page.coerceIn(0, source.pageCount - 1)) }
            },
            onAddBookmark = { title -> vm.addBookmark(title, listState.firstVisibleItemIndex) },
            onRemoveBookmark = vm::removeBookmark,
            onRemoveNote = vm::removeNote,
            onDismiss = { marksDialog = false },
        )
    }
    if (gotoDialog && source != null) {
        TextInputDialog(
            title = "Go to page",
            label = "Page number (1-${source.pageCount})",
            initial = "",
            confirmLabel = "Go",
            onConfirm = { text ->
                val page = text.toIntOrNull()
                if (page != null && page in 1..source.pageCount) {
                    scope.launch { listState.scrollToItem(page - 1) }
                } else {
                    container.messenger.post("Enter a page from 1 to ${source.pageCount}.")
                }
                gotoDialog = false
            },
            onDismiss = { gotoDialog = false },
        )
    }
    if (renameDialog && document != null) {
        TextInputDialog(
            title = "Rename",
            label = "Name",
            initial = document.name,
            confirmLabel = "Rename",
            onConfirm = {
                vm.rename(it)
                renameDialog = false
            },
            onDismiss = { renameDialog = false },
        )
    }
    if (deleteDialog) {
        ConfirmDialog(
            title = "Delete this document?",
            message = "This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                deleteDialog = false
                vm.delete { app.pop() }
            },
            onDismiss = { deleteDialog = false },
        )
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}

@Composable
private fun MarksDialog(
    bookmarks: List<Bookmark>,
    notes: List<NoteInfo>,
    loading: Boolean,
    busy: Boolean,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onAddBookmark: (String) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
    onRemoveNote: (NoteInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmarks and comments") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Bookmarks (${bookmarks.size})") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Comments (${notes.size})") })
                }
                if (tab == 0) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(80) },
                            singleLine = true,
                            placeholder = { Text("Page ${currentPage + 1}") },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onAddBookmark(title)
                                title = ""
                            },
                            enabled = !busy,
                        ) { Text("Add") }
                    }
                }
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    tab == 0 && bookmarks.isEmpty() -> Text(
                        "No bookmarks yet. The Add button bookmarks page ${currentPage + 1}; it is stored inside the PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    tab == 1 && notes.isEmpty() -> Text(
                        "No comments in this document. Add sticky notes with Edit, then Note.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    tab == 0 -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(bookmarks) { _, bookmark ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(bookmark.page) }
                                    .padding(start = (bookmark.level * 16).dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(bookmark.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "Page ${bookmark.page + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onRemoveBookmark(bookmark) }, enabled = !busy) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark ${bookmark.title}")
                                }
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(notes) { _, note ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onJump(note.page) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "Page ${note.page + 1}" + if (note.author.isNotEmpty()) ", ${note.author}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onRemoveNote(note) }, enabled = !busy) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove comment on page ${note.page + 1}")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun UnreadableMessage(canUnlock: Boolean, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("This PDF cannot be shown", style = MaterialTheme.typography.titleMedium)
        Text(
            "It may be password-protected or damaged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canUnlock) TextButton(onClick = onUnlock) { Text("Remove the password") }
    }
}

@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    matchCount: Int,
    matchIndex: Int,
    noTextLayer: Boolean,
    onQuery: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenOcr: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Find in this document") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPrevious, enabled = matchCount > 0) { Text("Prev") }
            TextButton(onClick = onNext, enabled = matchCount > 0) { Text("Next") }
        }
        val status = when {
            searching -> "Searching..."
            query.isBlank() -> ""
            matchCount == 0 && noTextLayer -> "This PDF has no text layer. Use Text to read it with OCR."
            matchCount == 0 -> "No matches"
            else -> "Match ${matchIndex + 1} of $matchCount (pages with the word)"
        }
        if (status.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (noTextLayer && matchCount == 0) TextButton(onClick = onOpenOcr) { Text("Read text") }
            }
        }
    }
}
