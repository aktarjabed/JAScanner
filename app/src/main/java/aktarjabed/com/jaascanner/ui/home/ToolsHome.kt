package aktarjabed.com.jaascanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aktarjabed.com.jaascanner.ui.nav.ToolKind

private data class ToolEntry(val title: String, val description: String, val action: () -> Unit)

@Composable
fun ToolsHomeContent(
    onTool: (ToolKind) -> Unit,
    onOcr: () -> Unit,
    onQr: () -> Unit,
    onCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = buildList {
        add(ToolEntry("Text from image", "Read text out of a photo (OCR)", onOcr))
        add(ToolEntry("ID card scan", "Front, back and more sides, from camera or gallery", onCards))
        add(ToolEntry("QR and barcodes", "Scan a code or create a QR code", onQr))
        ToolKind.entries.forEach { kind -> add(ToolEntry(kind.title, kind.description) { onTool(kind) }) }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Everything runs on your phone. Your documents are never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(entries, key = { it.title }) { entry ->
            Card(Modifier.fillMaxWidth().heightIn(min = 112.dp).clickable(onClick = entry.action)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
