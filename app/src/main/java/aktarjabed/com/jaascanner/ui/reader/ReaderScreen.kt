package aktarjabed.com.jaascanner.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen

private val PAPERS = listOf(
    "White" to (Color(0xFFFFFFFF) to Color(0xFF1B1B1B)),
    "Sepia" to (Color(0xFFF4ECD8) to Color(0xFF3B2F1E)),
    "Dark" to (Color(0xFF121212) to Color(0xFFE0E0E0)),
)
private val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f)
private const val MIN_TEXT_SP = 12
private const val MAX_TEXT_SP = 32

/**
 * Reading mode: the PDF's text reflowed to fit the phone screen with a size you choose, and
 * optional read-aloud. Only works on PDFs that have real text (scans need OCR first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(app: AppViewModel, documentId: Long) {
    val context = LocalContext.current
    val container = context.container
    val vm = containerViewModel { ReaderViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var textSize by rememberSaveable { mutableIntStateOf(18) }
    var paper by rememberSaveable { mutableIntStateOf(0) }
    var serif by rememberSaveable { mutableStateOf(true) }
    var speed by rememberSaveable { mutableIntStateOf(1) }
    var menu by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var spokenParagraph by remember { mutableIntStateOf(-1) }

    val reader = remember {
        ReadAloud(
            context = context,
            onParagraph = { spokenParagraph = it },
            onPlaying = { speaking = it },
            onProblem = { container.messenger.post(it) },
        )
    }
    DisposableEffect(Unit) { onDispose { reader.shutdown() } }

    // Keep the paragraph being read in view.
    LaunchedEffect(spokenParagraph, speaking) {
        if (speaking && spokenParagraph >= 0) {
            val row = state.rows.indexOfFirst { it.paragraph == spokenParagraph }
            if (row >= 0) listState.animateScrollToItem(row)
        }
    }

    val (paperColor, inkColor) = PAPERS[paper].second

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(state.document?.name ?: "Reading mode", maxLines = 1) },
                actions = {
                    TextButton(onClick = { textSize = (textSize - 2).coerceAtLeast(MIN_TEXT_SP) }) { Text("A-") }
                    TextButton(onClick = { textSize = (textSize + 2).coerceAtMost(MAX_TEXT_SP) }) { Text("A+") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Style") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (serif) "Font: serif (tap for plain)" else "Font: plain (tap for serif)") },
                                onClick = {
                                    serif = !serif
                                    menu = false
                                },
                            )
                            PAPERS.forEachIndexed { index, (name, _) ->
                                DropdownMenuItem(
                                    text = { Text(if (paper == index) "Paper: $name (selected)" else "Paper: $name") },
                                    onClick = {
                                        paper = index
                                        menu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.hasText) {
                BottomAppBar(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    TextButton(
                        onClick = {
                            when {
                                speaking -> reader.pause()
                                spokenParagraph >= 0 -> reader.resume()
                                else -> {
                                    reader.setRate(SPEEDS[speed])
                                    val firstShown = state.rows.drop(listState.firstVisibleItemIndex).firstOrNull { it.paragraph != null }
                                    reader.start(state.paragraphs, firstShown?.paragraph ?: 0)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (speaking) "Pause" else if (spokenParagraph >= 0) "Resume" else "Read aloud") }
                    TextButton(
                        onClick = {
                            reader.stop()
                            spokenParagraph = -1
                        },
                        enabled = speaking || spokenParagraph >= 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop") }
                    TextButton(
                        onClick = {
                            speed = (speed + 1) % SPEEDS.size
                            reader.setRate(SPEEDS[speed])
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Speed ${SPEEDS[speed]}x") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(paperColor)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error.orEmpty(),
                    color = inkColor,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                !state.hasText -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("This PDF has no text to reflow", style = MaterialTheme.typography.titleMedium, color = inkColor)
                    Text(
                        "It looks like a scan. Read it with OCR first; saving a searchable copy then lets reading mode and read-aloud work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkColor,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    TextButton(onClick = { app.replaceTop(Screen.Ocr(documentId)) }) { Text("Read it with OCR") }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(state.rows) { _, row ->
                        if (row.paragraph == null) {
                            Text(
                                row.text,
                                style = MaterialTheme.typography.labelMedium,
                                color = inkColor.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            val current = speaking && row.paragraph == spokenParagraph
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (current) inkColor.copy(alpha = 0.12f) else Color.Transparent),
                            ) {
                                Text(
                                    row.text,
                                    color = inkColor,
                                    fontSize = textSize.sp,
                                    lineHeight = (textSize * 1.55f).sp,
                                    fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
