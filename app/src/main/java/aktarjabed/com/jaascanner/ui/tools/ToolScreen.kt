package aktarjabed.com.jaascanner.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.pdf.ImageDecoding
import aktarjabed.com.jaascanner.pdf.ImageFilter
import aktarjabed.com.jaascanner.pdf.PageImageExporter
import aktarjabed.com.jaascanner.pdf.PageSizeOption
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.SectionTitle
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import aktarjabed.com.jaascanner.ui.nav.ToolKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(app: AppViewModel, kind: ToolKind, preselected: List<Long>) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { ToolViewModel(it, kind, preselected) }
    val state by vm.state.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    val watermarkImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWatermarkImage(uri)
    }

    val usesDocuments = kind != ToolKind.ImagesToPdf
    val pickable = state.documents.filter { kind == ToolKind.Unlock || it.pageCount > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(kind.title) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    kind.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (usesDocuments) {
                item {
                    SectionTitle(if (vm.allowsManyDocuments) "Choose documents, in the order you want them" else "Choose a document")
                }
                if (pickable.isEmpty()) {
                    item {
                        Text(
                            "There are no documents to use yet. Scan or import one first.",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(pickable, key = { it.id }) { doc ->
                    val position = state.selected.indexOf(doc.id)
                    val selected = position >= 0
                    ListItem(
                        headlineContent = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                (if (doc.pageCount == 0) "Locked" else "${doc.pageCount} pages") + " · " + Formatters.fileSize(doc.sizeBytes),
                            )
                        },
                        leadingContent = {
                            if (vm.allowsManyDocuments) {
                                Checkbox(checked = selected, onCheckedChange = null)
                            } else {
                                RadioButton(selected = selected, onClick = null)
                            }
                        },
                        trailingContent = {
                            if (vm.allowsManyDocuments && selected) Text("${position + 1}", style = MaterialTheme.typography.titleMedium)
                        },
                        modifier = Modifier.clickable { vm.toggleDocument(doc.id) },
                    )
                }
            } else {
                item {
                    Column {
                        SectionTitle("Pictures")
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text(if (state.images.isEmpty()) "Pick pictures" else "Add more") }
                            OutlinedButton(onClick = { filePicker.launch(arrayOf("image/*")) }) { Text("From files") }
                            if (state.images.isNotEmpty()) {
                                Text("${state.images.size} chosen", modifier = Modifier.weight(1f))
                                TextButton(onClick = vm::clearImages) { Text("Clear") }
                            }
                        }
                    }
                }
                itemsIndexed(state.images, key = { index, uri -> "$index:$uri" }) { index, uri ->
                    ListItem(
                        headlineContent = { Text("Page ${index + 1}") },
                        leadingContent = { PictureThumb(uri) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { vm.moveImage(index, -1) }, enabled = index > 0) { Text("Up") }
                                TextButton(
                                    onClick = { vm.moveImage(index, 1) },
                                    enabled = index < state.images.size - 1,
                                ) { Text("Down") }
                                TextButton(onClick = { vm.removeImage(index) }) { Text("Remove") }
                            }
                        },
                    )
                }
            }

            if (kind == ToolKind.InsertFromPdf) {
                item { SectionTitle("Take pages from") }
                items(pickable, key = { "src-" + it.id }) { doc ->
                    ListItem(
                        headlineContent = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${doc.pageCount} pages") },
                        leadingContent = { RadioButton(selected = state.insertSource == doc.id, onClick = null) },
                        modifier = Modifier.clickable { vm.setInsertSource(doc.id) },
                    )
                }
            }

            item { SectionTitle("Options") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (kind) {
                        ToolKind.Extract, ToolKind.DeletePages -> PageField(state.pageText, "Pages, for example 1-3, 5, 8-", vm::setPageText)
                        ToolKind.ExportImages -> PageField(state.pageText, "Pages (leave empty for all)", vm::setPageText)
                        ToolKind.Rotate -> {
                            PageField(state.pageText, "Pages (leave empty for all)", vm::setPageText)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(90 to "Right 90°", 180 to "Flip 180°", 270 to "Left 90°").forEach { (degrees, label) ->
                                    FilterChip(
                                        selected = state.degrees == degrees,
                                        onClick = { vm.setDegrees(degrees) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                        ToolKind.Split -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !state.splitByRanges,
                                    onClick = { vm.setSplitByRanges(false) },
                                    label = { Text("Equal parts") },
                                )
                                FilterChip(
                                    selected = state.splitByRanges,
                                    onClick = { vm.setSplitByRanges(true) },
                                    label = { Text("Named ranges") },
                                )
                            }
                            if (state.splitByRanges) {
                                PageField(state.rangesText, "Ranges, separated by ; for example 1-3; 4-9; 10", vm::setRangesText)
                            } else {
                                OutlinedTextField(
                                    value = state.perFile,
                                    onValueChange = vm::setPerFile,
                                    label = { Text("Pages in each file") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        ToolKind.Reorder -> {
                            PageField(state.orderText, "New order, for example 3,1,2", vm::setOrderText)
                            Text(
                                "List every page number once, in the order you want them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolKind.InsertBlank -> {
                            PageField(state.insertAfter, "Insert after page (0 = at the start)", vm::setInsertAfter)
                            PageField(state.blankCount, "How many blank pages", vm::setBlankCount)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageSizeOption.entries.filter { it != PageSizeOption.Fit }.forEach { option ->
                                    FilterChip(
                                        selected = state.pageSize == option,
                                        onClick = { vm.setPageSize(option) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                        }
                        ToolKind.InsertFromPdf -> {
                            PageField(state.insertAfter, "Insert after page (0 = at the start)", vm::setInsertAfter)
                            PageField(state.pageText, "Pages to insert (leave empty for all)", vm::setPageText)
                        }
                        ToolKind.Crop -> {
                            PageField(state.pageText, "Pages (leave empty for all)", vm::setPageText)
                            Text("Trim ${state.cropPercent}% from each edge", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = state.cropPercent.toFloat(),
                                onValueChange = { vm.setCropPercent(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28,
                            )
                        }
                        ToolKind.Protect -> {
                            PasswordField(state.password, "Password (at least 4 characters)", vm::setPassword)
                            Text(
                                "If you forget this password the file cannot be opened. JAScanner cannot recover it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolKind.Unlock -> PasswordField(state.password, "Current password", vm::setPassword)
                        ToolKind.Watermark -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.watermarkMode == WatermarkMode.Text,
                                    onClick = { vm.setWatermarkMode(WatermarkMode.Text) },
                                    label = { Text("Text") },
                                )
                                FilterChip(
                                    selected = state.watermarkMode == WatermarkMode.Picture,
                                    onClick = { vm.setWatermarkMode(WatermarkMode.Picture) },
                                    label = { Text("Picture") },
                                )
                            }
                            if (state.watermarkMode == WatermarkMode.Text) {
                                OutlinedTextField(
                                    value = state.watermarkText,
                                    onValueChange = vm::setWatermarkText,
                                    label = { Text("Watermark text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.watermarkImage?.let { PictureThumb(it) }
                                    OutlinedButton(onClick = {
                                        watermarkImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }) { Text(if (state.watermarkImage == null) "Choose a picture" else "Change picture") }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.setWatermarkTiled(!state.watermarkTiled) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = state.watermarkTiled, onCheckedChange = vm::setWatermarkTiled)
                                Text("Repeat across the whole page, not just the centre")
                            }
                            Text("Strength ${(state.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                            Slider(value = state.opacity, onValueChange = vm::setOpacity, valueRange = 0.1f..0.8f)
                        }
                        ToolKind.ImagesToPdf -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Page size", style = MaterialTheme.typography.labelLarge)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PageSizeOption.entries.forEach { option ->
                                    FilterChip(
                                        selected = state.pageSize == option,
                                        onClick = { vm.setPageSize(option) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                            Text("Clean-up", style = MaterialTheme.typography.labelLarge)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ImageFilter.entries.forEach { option ->
                                    FilterChip(
                                        selected = state.imageFilter == option,
                                        onClick = { vm.setImageFilter(option) },
                                        label = { Text(option.label) },
                                    )
                                }
                            }
                            Text(
                                "Black and white copes with shadows on photographed pages and makes small files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolKind.Compress -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.compressMode == CompressMode.KeepText,
                                    onClick = { vm.setCompressMode(CompressMode.KeepText) },
                                    label = { Text("Keep text (recommended)") },
                                )
                                FilterChip(
                                    selected = state.compressMode == CompressMode.Smallest,
                                    onClick = { vm.setCompressMode(CompressMode.Smallest) },
                                    label = { Text("Smallest") },
                                )
                            }
                            Text(
                                if (state.compressMode == CompressMode.KeepText) {
                                    "Shrinks the pictures in the file at the quality set in Settings; real, selectable text " +
                                        "stays real text. A PDF with no pictures will not get smaller this way."
                                } else {
                                    "Turns every page into a picture at the quality set in Settings. Usually the smallest " +
                                        "file, but any text is read again by OCR rather than selected directly."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolKind.Merge -> Unit
                    }
                    if (kind != ToolKind.ExportImages) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = vm::setName,
                            label = { Text("Name of the new file (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.run(context) },
                        enabled = vm.canRun(state),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.running) "Working..." else "Run") }
                    if (state.running) {
                        val progress = state.progress
                        if (progress != null && progress.second > 0) {
                            LinearProgressIndicator(
                                progress = { progress.first.toFloat() / progress.second },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${progress.first} of ${progress.second}", style = MaterialTheme.typography.labelMedium)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            state.message?.let { message ->
                item {
                    val success = state.results.isNotEmpty() || state.exported.isNotEmpty()
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (success) "Done" else "Could not finish", style = MaterialTheme.typography.titleSmall)
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            items(state.results, key = { it.id }) { result ->
                ListItem(
                    headlineContent = { Text(result.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text((if (result.pageCount == 0) "Locked" else "${result.pageCount} pages") + " · " + Formatters.fileSize(result.sizeBytes))
                    },
                    trailingContent = {
                        Row {
                            if (result.pageCount > 0) {
                                TextButton(onClick = { app.replaceTop(Screen.Viewer(result.id)) }) { Text("Open") }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ShareHelper.sharePdf(context, container.documents.fileOf(result), result.name)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        container.messenger.post(error.toUserMessage())
                                    }
                                }
                            }) { Text("Share") }
                        }
                    },
                )
            }

            if (state.exported.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    ShareHelper.shareImages(context, state.exported)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    container.messenger.post(error.toUserMessage())
                                }
                            }
                        }) { Text("Share pictures") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val saved = PageImageExporter.saveToGallery(context, state.exported)
                                container.messenger.post(
                                    if (saved > 0) "Saved $saved pictures to Pictures/JAScanner." else "Could not save the pictures.",
                                )
                            }
                        }) { Text("Save to gallery") }
                    }
                }
            }
        }
    }
}

/** Small preview of a chosen picture, decoded off the main thread. */
@Composable
private fun PictureThumb(uri: android.net.Uri) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ImageDecoding.decode(context, uri, maxSide = 160) }.getOrNull()
        }
    }
    Box(Modifier.size(width = 48.dp, height = 64.dp).background(androidx.compose.ui.graphics.Color.LightGray)) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PageField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
