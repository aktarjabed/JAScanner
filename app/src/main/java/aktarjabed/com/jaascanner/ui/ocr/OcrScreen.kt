package aktarjabed.com.jaascanner.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.data.OcrLanguage
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.containerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(app: AppViewModel, documentId: Long?) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { OcrViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val document = state.document

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.readImage(context, uri)
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(state.plainText.toByteArray(Charsets.UTF_8)) }
                        true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        false
                    }
                }
                container.messenger.post(if (saved) "Text file saved." else "Could not save the text file.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(if (document != null) "Text in ${document.name}" else "Read text from a picture", maxLines = 1) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = state.language == language,
                        onClick = { vm.setLanguage(language) },
                        label = { Text(if (language == OcrLanguage.Latin) "Latin" else "Devanagari") },
                    )
                }
            }
            Text(
                "Pick the script that matches your document, then read it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (document != null) {
                    Button(onClick = vm::readDocument, enabled = !state.running) {
                        Text(if (state.hasRun) "Read again" else "Read text")
                    }
                }
                OutlinedButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.running,
                ) { Text("Read a picture") }
            }

            if (document != null && document.pageCount > OcrViewModel.MAX_PAGES) {
                Text(
                    "Only the first ${OcrViewModel.MAX_PAGES} pages are read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.running) {
                val progress = state.progress
                if (progress != null && progress.second > 0) {
                    LinearProgressIndicator(progress = { progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
                    Text("Page ${progress.first} of ${progress.second}", style = MaterialTheme.typography.labelMedium)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (state.hasRun && !state.running && state.plainText.isBlank() && state.error == null) {
                Text("No text was found. Try the other script, or scan again in better light.")
            }

            if (state.plainText.isNotBlank()) {
                SelectionContainer {
                    Text(state.displayText, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        copyToClipboard(context, state.plainText)
                        container.messenger.post("Text copied.")
                    }) { Text("Copy") }
                    TextButton(onClick = { ShareHelper.shareText(context, state.plainText) }) { Text("Share") }
                    TextButton(onClick = {
                        val base = document?.name ?: "Text from picture"
                        saveLauncher.launch(Formatters.safeFileName(base) + ".txt")
                    }) { Text("Save .txt") }
                }
                if (document != null) {
                    OutlinedButton(onClick = vm::makeSearchable) { Text("Make this document searchable in the library") }
                    Button(onClick = vm::saveSearchablePdf, enabled = state.canEmbed && !state.running && !state.embedding) {
                        Text(if (state.embedding) "Saving..." else "Save a searchable PDF copy")
                    }
                    Text(
                        if (state.canEmbed) {
                            "The text is hidden under each scanned page, so any PDF reader can search and select it. Your original is not changed."
                        } else {
                            "To save a searchable PDF, tap Read again first: the saved text has no word positions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("JAScanner text", text))
}
