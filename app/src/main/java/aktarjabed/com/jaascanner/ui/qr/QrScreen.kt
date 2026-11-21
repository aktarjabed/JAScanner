package aktarjabed.com.jaascanner.ui.qr

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.awaitResult
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.ui.AppViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(app: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("QR and barcodes") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Scan") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Create") })
            }
            if (tab == 0) ScanTab() else CreateTab()
        }
    }
}

@Composable
private fun ScanTab() {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Point the camera at a QR code or barcode. The scanner is provided by Google Play services, " +
                "so JAScanner itself needs no camera permission.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            scope.launch {
                try {
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                    val barcode = GmsBarcodeScanning.getClient(context, options).startScan().awaitResult()
                    result = barcode.rawValue ?: barcode.displayValue
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val cancelled = error.message?.contains("cancel", ignoreCase = true) == true
                    if (!cancelled) container.messenger.post("The code scanner is not available on this device.")
                }
            }
        }) { Text("Scan a code") }

        val scanned = result
        if (scanned != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scanned", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(scanned, style = MaterialTheme.typography.bodyLarge) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            copy(context, scanned)
                            container.messenger.post("Copied.")
                        }) { Text("Copy") }
                        TextButton(onClick = { ShareHelper.shareText(context, scanned) }) { Text("Share") }
                        if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(scanned)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (error: ActivityNotFoundException) {
                                    container.messenger.post("No app can open this link.")
                                }
                            }) { Text("Open link") }
                        }
                    }
                    if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
                        Text(
                            "Check the address before you open it. Links in codes can lead to fake sites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTab() {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var qr by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(1_000)
                qr = null
            },
            label = { Text("Text or web address") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                qr = try {
                    createQr(text.trim())
                } catch (error: Exception) {
                    container.messenger.post("That text is too long for a QR code.")
                    null
                }
            },
            enabled = text.isNotBlank(),
        ) { Text("Create QR code") }

        qr?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code",
                modifier = Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.White),
            )
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "qr-code.png").also { target ->
                                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                        ShareHelper.sharePng(context, file, "JAScanner QR code")
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        container.messenger.post("Could not share the QR code.")
                    }
                }
            }) { Text("Share picture") }
        }
    }
}

private fun createQr(text: String, size: Int = 768): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { index ->
        if (matrix.get(index % width, index / width)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun copy(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("JAScanner", text))
}
