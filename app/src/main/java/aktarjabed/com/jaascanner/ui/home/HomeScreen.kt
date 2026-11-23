package aktarjabed.com.jaascanner.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.FolderPath
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.data.db.FolderEntity
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.scan.ScannerLauncher
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.ConfirmDialog
import aktarjabed.com.jaascanner.ui.components.TextInputDialog
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import aktarjabed.com.jaascanner.ui.nav.ToolKind
import aktarjabed.com.jaascanner.ui.settings.SettingsContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppViewModel) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { LibraryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var newFolderForMove by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var inputNonce by rememberSaveable { mutableIntStateOf(0) }
    var scanMenu by remember { mutableStateOf(false) }
    // Saved so the guard survives an activity recreation while the scanner is open.
    var scanStarting by rememberSaveable { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val selectedDocs = state.documents.filter { it.id in state.selection }
    val selectedIds = selectedDocs.map { it.id }

    BackHandler(enabled = selectedDocs.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = tab != 0 && selectedDocs.isEmpty()) { tab = 0 }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // The scanner has returned (even if the user cancelled), so allow another scan now.
        scanStarting = false
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = ScannerLauncher.parseResult(result.data)
            if (scan == null) {
                container.messenger.post("No pages were captured.")
            } else {
                vm.saveScan(scan) { id -> app.push(Screen.Viewer(id)) }
            }
        }
    }

    val startScan: () -> Unit = {
        val activity = context.findActivity()
        // Guards a fast double-tap on the scan button: without this, a second tap before the
        // scanner's IntentSender comes back could launch scanLauncher twice, which can throw on
        // some OEMs ("StartIntentSenderForResult can only be used once").
        if (activity != null && !scanStarting) {
            scanStarting = true
            scope.launch {
                try {
                    val settings = container.settings.settings.first()
                    val sender = ScannerLauncher.startIntentSender(activity, settings)
                    scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    // Keep the guard set until the result callback runs. Clearing it here would
                    // reopen the window a second tap could use while the scanner is still open.
                } catch (error: CancellationException) {
                    // The scanner was never launched if the scope died during the settings read.
                    scanStarting = false
                    throw error
                } catch (error: Exception) {
                    scanStarting = false
                    // Google's scanner is a Play services module and is missing on some phones
                    // (and out of date on others). The built-in camera is the fallback.
                    container.messenger.post("Google's scanner is not available. Opening this phone's camera instead.")
                    app.push(Screen.CameraScan())
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri) { id -> app.push(Screen.Viewer(id)) }
    }
    val importPdf: () -> Unit = { importLauncher.launch(arrayOf("application/pdf")) }

    LaunchedEffect(app.pendingScan) {
        if (app.pendingScan) {
            app.consumeScanRequest()
            tab = 0
            startScan()
        }
    }

    Scaffold(
        topBar = {
            if (tab == 0 && selectedDocs.isNotEmpty()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
                    },
                    title = { Text("${selectedDocs.size} selected") },
                    actions = {
                        if (selectedDocs.size < state.documents.size) {
                            TextButton(onClick = vm::selectAll) { Text("All") }
                        }
                        IconButton(
                            enabled = !sharing,
                            onClick = {
                                if (sharing) return@IconButton
                                sharing = true
                                val chosen = selectedDocs.map { container.documents.fileOf(it) to it.name }
                                scope.launch {
                                    try {
                                        ShareHelper.sharePdfs(context, chosen)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        container.messenger.post(error.toUserMessage())
                                    } finally {
                                        sharing = false
                                    }
                                }
                            },
                        ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                if (selectedDocs.size == 1) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            overflow = false
                                            inputNonce++
                                            renameTarget = selectedDocs.first().id
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Pin to top") },
                                    onClick = {
                                        overflow = false
                                        vm.setPinnedMany(selectedIds, true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Unpin") },
                                    onClick = {
                                        overflow = false
                                        vm.setPinnedMany(selectedIds, false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to favorites") },
                                    onClick = {
                                        overflow = false
                                        vm.setFavoriteMany(selectedIds, true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from favorites") },
                                    onClick = {
                                        overflow = false
                                        vm.setFavoriteMany(selectedIds, false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to folder") },
                                    onClick = {
                                        overflow = false
                                        moveDialog = true
                                    },
                                )
                                if (selectedDocs.size >= 2) {
                                    DropdownMenuItem(
                                        text = { Text("Merge into one PDF") },
                                        onClick = {
                                            overflow = false
                                            app.push(Screen.Tool(ToolKind.Merge, selectedIds))
                                            vm.clearSelection()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(listOf("JAScanner", "Tools", "Settings")[tab]) },
                    actions = {
                        if (tab == 0) TextButton(onClick = importPdf) { Text("Import PDF") }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                    label = { Text("Tools") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0 && selectedDocs.isEmpty()) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { scanMenu = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Scan") },
                    )
                    DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Scan document") },
                            onClick = {
                                scanMenu = false
                                startScan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("ID card (front, back, more)") },
                            onClick = {
                                scanMenu = false
                                app.push(Screen.Cards())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Scan with this phone's camera") },
                            onClick = {
                                scanMenu = false
                                app.push(Screen.CameraScan())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Photos to PDF") },
                            onClick = {
                                scanMenu = false
                                app.push(Screen.Tool(ToolKind.ImagesToPdf))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import a PDF") },
                            onClick = {
                                scanMenu = false
                                importPdf()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> LibraryContent(
                state = state,
                vm = vm,
                onOpen = { doc -> app.push(Screen.Viewer(doc.id)) },
                onImport = importPdf,
                modifier = Modifier.padding(padding),
            )
            1 -> ToolsHomeContent(
                onTool = { kind -> app.push(Screen.Tool(kind)) },
                onOcr = { app.push(Screen.Ocr(null)) },
                onQr = { app.push(Screen.Qr()) },
                onCards = { app.push(Screen.Cards()) },
                modifier = Modifier.padding(padding),
            )
            else -> SettingsContent(modifier = Modifier.padding(padding))
        }
    }

    renameTarget?.let { id ->
        val doc = state.documents.firstOrNull { it.id == id }
        if (doc != null) {
            TextInputDialog(
                title = "Rename",
                label = "Name",
                initial = doc.name,
                confirmLabel = "Rename",
                dialogKey = Pair(id, inputNonce),
                onConfirm = {
                    vm.rename(id, it)
                    vm.clearSelection()
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        } else {
            renameTarget = null
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = if (selectedIds.size == 1) "Delete this document?" else "Delete ${selectedIds.size} documents?",
            message = "This cannot be undone. Shared copies stay with whoever you sent them to.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(selectedIds)
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (moveDialog) {
        MoveDialog(
            folders = state.folders,
            onMove = { folderId ->
                vm.moveTo(selectedIds, folderId)
                moveDialog = false
            },
            onNewFolder = {
                moveDialog = false
                inputNonce++
                newFolderForMove = true
            },
            onDismiss = { moveDialog = false },
        )
    }
    if (newFolderForMove) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmLabel = "Create and move",
            dialogKey = Pair("newFolderForMove", inputNonce),
            onConfirm = {
                vm.createFolder(it, thenMove = selectedIds)
                newFolderForMove = false
            },
            onDismiss = { newFolderForMove = false },
        )
    }
}

@Composable
private fun MoveDialog(
    folders: List<FolderEntity>,
    onMove: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onMove(null) }) { Text("No folder") }
                folders.forEach { folder ->
                    TextButton(onClick = { onMove(folder.id) }) { Text(FolderPath.label(folders, folder.id)) }
                }
                TextButton(onClick = onNewFolder) { Text("+ New folder") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
