package aktarjabed.com.jaascanner.ui.cards

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.pdf.ImageDecoding
import aktarjabed.com.jaascanner.pdf.ImageFilter
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.scan.ScannerLauncher
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScanScreen(app: AppViewModel, screenId: Long) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val vm = containerViewModel { CardScanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Survives process death: a picture taken after a restart must still go to the right side.
    var target by rememberSaveable { mutableIntStateOf(0) }
    val targetSide = target.takeIf { it in state.sides.indices }
    // A second tap before the camera/gallery returns can launch the same contract twice, which
    // throws on some OEMs. Kept across recreation because the scanner is an external Activity.
    var scanStarting by rememberSaveable { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scanStarting = false
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = ScannerLauncher.parseCardResult(result.data)
            val index = targetSide
            if (uri == null) {
                container.messenger.post("No picture was captured.")
            } else if (index == null) {
                container.messenger.post("That side is no longer available.")
            } else {
                vm.setSide(context, index, uri)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        scanStarting = false
        val index = targetSide
        if (uri != null && index != null) vm.setSide(context, index, uri)
    }

    val scanSide: (Int) -> Unit = { index ->
        if (!scanStarting) {
            scanStarting = true
            target = index
            val activity = context.findActivity()
            if (activity == null) {
                scanStarting = false
            } else {
                scope.launch {
                    try {
                        val sender = ScannerLauncher.startCardIntentSender(activity)
                        scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } catch (error: CancellationException) {
                        scanStarting = false
                        throw error
                    } catch (error: Exception) {
                        scanStarting = false
                        container.messenger.post("Google's scanner is not available. Use Gallery below to add this side instead.")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("ID card scan") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Capture each side of the card with the camera, or pick it from the gallery; the scanner " +
                            "finds the card's edges and straightens it. Add more sides for cards with more than two.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(state.sides, key = { index, _ -> index }) { index, side ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(side.label, style = MaterialTheme.typography.titleSmall)
                            if (side.photo != null) {
                                SidePreview(side.photo)
                            } else {
                                Text("Not captured yet", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { scanSide(index) }, enabled = !scanStarting) {
                                    Text(if (side.photo == null) "Scan" else "Rescan")
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (scanStarting) return@OutlinedButton
                                        scanStarting = true
                                        target = index
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    enabled = !scanStarting,
                                ) { Text("Gallery") }
                                if (side.photo != null) TextButton(onClick = { vm.clearSide(index) }) { Text("Remove") }
                            }
                        }
                    }
                }
                state.pictureError?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::addSide, enabled = state.sides.size < MAX_SIDES) { Text("Add a side") }
                        TextButton(onClick = vm::removeLastSide, enabled = state.sides.size > 1) { Text("Remove last side") }
                    }
                }
                item {
                    Text("Layout", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.onePage, onClick = { vm.setOnePage(true) }, label = { Text("All sides on one A4 page") })
                        FilterChip(selected = !state.onePage, onClick = { vm.setOnePage(false) }, label = { Text("One page per side") })
                    }
                }
                item {
                    Text("Look", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageFilter.entries.forEach { filter ->
                            FilterChip(selected = state.filter == filter, onClick = { vm.setFilter(filter) }, label = { Text(filter.label) })
                        }
                    }
                }
                item {
                    Button(
                        onClick = { vm.save(context) { id -> app.replaceTopIfCurrent(screenId, Screen.Viewer(id)) } },
                        enabled = !state.saving && state.sides.any { it.photo != null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save as PDF") }
                }
            }
            if (state.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SidePreview(file: File) {
    val context = LocalContext.current
    val container = context.container
    var failed by remember(file) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, file) {
        val decoded = withContext(Dispatchers.IO) {
            try {
                Result.success(ImageDecoding.decode(context, file.toUri(), maxSide = 700))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        if (decoded.isSuccess) {
            value = decoded.getOrNull()
        } else {
            decoded.exceptionOrNull()?.let { container.messenger.post(it.toUserMessage()) }
            failed = true
        }
    }
    val shown = bitmap
    when {
        shown != null -> Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        failed -> Text("Preview unavailable", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}
