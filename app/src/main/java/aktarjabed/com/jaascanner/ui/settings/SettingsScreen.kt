package aktarjabed.com.jaascanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.core.findActivity
import aktarjabed.com.jaascanner.data.AppSettings
import aktarjabed.com.jaascanner.data.OcrLanguage
import aktarjabed.com.jaascanner.data.ScanMode
import aktarjabed.com.jaascanner.data.ThemeMode
import aktarjabed.com.jaascanner.data.db.FolderEntity
import aktarjabed.com.jaascanner.security.AppLock
import aktarjabed.com.jaascanner.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Number of documents and total bytes they use. */
    val stats = MutableStateFlow(0 to 0L)

    val folders: StateFlow<List<FolderEntity>> = container.documents.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDefaultFolder(id: Long?) = launchSet { container.settings.setDefaultFolderId(id) }

    fun refreshStats() {
        viewModelScope.launch { stats.value = container.documents.count() to container.documents.totalSize() }
    }

    fun setTheme(value: ThemeMode) = launchSet { container.settings.setThemeMode(value) }

    fun setUseDynamicColor(value: Boolean) = launchSet { container.settings.setUseDynamicColor(value) }

    fun setScanMode(value: ScanMode) = launchSet { container.settings.setScanMode(value) }

    fun setPageLimit(value: Int) = launchSet { container.settings.setPageLimit(value) }

    fun setAutoOcr(value: Boolean) = launchSet { container.settings.setAutoOcr(value) }

    fun setOcrLanguage(value: OcrLanguage) = launchSet { container.settings.setOcrLanguage(value) }

    fun setAppLock(value: Boolean) = launchSet { container.settings.setAppLock(value) }

    fun setBlockScreenshots(value: Boolean) = launchSet { container.settings.setBlockScreenshots(value) }

    fun setCompressQuality(value: Int) = launchSet { container.settings.setCompressQuality(value) }

    fun forgetEditorMemory() {
        launchSet { container.settings.clearEditorMemory() }
        container.messenger.post("Editor memory cleared.")
    }

    private fun launchSet(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.container
    val vm = containerViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStats() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup("Appearance") {
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = when (mode) {
                        ThemeMode.System -> "Follow system"
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                    },
                    selected = settings.themeMode == mode,
                    onSelect = { vm.setTheme(mode) },
                )
            }
            RowDivider()
            SwitchRow(
                title = "Match my wallpaper",
                subtitle = "Material You colours from your phone's wallpaper, instead of JAScanner's own blue and amber",
                checked = settings.useDynamicColor,
                onChange = vm::setUseDynamicColor,
            )
        }

        SettingsGroup("Scanning") {
            ScanMode.entries.forEach { mode ->
                RadioRow(label = mode.label, selected = settings.scanMode == mode, onSelect = { vm.setScanMode(mode) })
            }
            SliderRow(
                title = "Pages per scan",
                valueLabel = "${settings.pageLimit}",
                value = settings.pageLimit.toFloat(),
                range = 1f..500f,
                onCommit = { vm.setPageLimit(it.toInt()) },
            )
        }

        SettingsGroup("Text recognition (OCR)") {
            SwitchRow(
                title = "Read text after every scan",
                subtitle = "Lets you search inside scans from the library",
                checked = settings.autoOcr,
                onChange = vm::setAutoOcr,
            )
            RowDivider()
            OcrLanguage.entries.forEach { language ->
                RadioRow(label = language.label, selected = settings.ocrLanguage == language, onSelect = { vm.setOcrLanguage(language) })
            }
        }

        SettingsGroup("Default folder for new scans") {
            val folders by vm.folders.collectAsStateWithLifecycle()
            RadioRow(
                label = "No folder",
                selected = settings.defaultFolderId == null,
                onSelect = { vm.setDefaultFolder(null) },
            )
            folders.forEach { folder ->
                RadioRow(
                    label = aktarjabed.com.jaascanner.core.FolderPath.label(folders, folder.id),
                    selected = settings.defaultFolderId == folder.id,
                    onSelect = { vm.setDefaultFolder(folder.id) },
                )
            }
        }

        SettingsGroup("Output") {
            SliderRow(
                title = "Compression quality",
                valueLabel = "${settings.compressQuality}%",
                value = settings.compressQuality.toFloat(),
                range = 30f..90f,
                onCommit = { vm.setCompressQuality(it.toInt()) },
            )
        }

        SettingsGroup("Privacy & permissions") {
            Text(
                "What this app can see",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            PermissionRow(
                title = "Camera",
                detail = "JAScanner's own code never asks for it. Scanning opens Google's own scanner, which " +
                    "asks you for camera access itself, only while it's on screen; JAScanner never holds that permission.",
            )
            PermissionRow(
                title = "Photos and files",
                detail = "Picked one at a time through Android's own picker when you choose \"gallery\", " +
                    "\"from files\" or an image for a watermark. JAScanner is never granted standing access to " +
                    "your gallery or storage.",
            )
            PermissionRow(
                title = "Notifications",
                detail = "JAScanner shows none and asks for none.",
            )
            PermissionRow(
                title = "Internet",
                detail = "JAScanner's own code opens no network connection; nothing you scan or create is " +
                    "uploaded. Google Play services components (scanning, OCR, barcodes) may use their own, " +
                    "under Google's privacy practices — this is also the one honest exception to \"no internet\".",
            )
            PermissionRow(
                title = "Fingerprint, face or screen lock",
                detail = "Used only to unlock JAScanner, only if you turn on App lock below, through Android's " +
                    "own unlock prompt. JAScanner never sees your fingerprint or face, only whether you passed.",
                isLast = true,
            )
            RowDivider()
            SwitchRow(
                title = "App lock",
                subtitle = "Ask for fingerprint, face or screen lock when opening JAScanner",
                checked = settings.appLock,
                onChange = { enable ->
                    if (!enable) {
                        vm.setAppLock(false)
                    } else {
                        val activity = context.findActivity() as? FragmentActivity
                        if (activity == null || !AppLock.isAvailable(context)) {
                            container.messenger.post("Set up a screen lock or fingerprint in your phone settings first.")
                        } else {
                            AppLock.prompt(
                                activity = activity,
                                title = "Turn on app lock",
                                subtitle = "Confirm it is you",
                                onSuccess = { vm.setAppLock(true) },
                                onFailure = { container.messenger.post(it) },
                            )
                        }
                    }
                },
            )
            SwitchRow(
                title = "Hide in recent apps and screenshots",
                subtitle = "Blocks screenshots and blanks the app preview",
                checked = settings.blockScreenshots,
                onChange = vm::setBlockScreenshots,
            )
        }

        SettingsGroup("Editor memory") {
            Text(
                "The editor remembers your last pen and highlight colours, line width, typeface and the " +
                    "text you typed recently, only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            TextButton(onClick = vm::forgetEditorMemory, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Forget editor memory")
            }
        }

        SettingsGroup("About") {
            Text(
                "JAScanner keeps every document on this phone. It has no account and no ads, collects no " +
                    "analytics of its own, and never uploads your files.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "Library: ${stats.first} documents, ${Formatters.fileSize(stats.second)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "Version ${versionName(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * A rounded, tonal card for one group of related settings, with a title above it — the
 * grouped-sections look most modern settings screens use instead of one long flat list.
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** One line of the "what this app can see" permissions disclosure: an icon, a title and plain-language detail. */
@Composable
private fun PermissionRow(title: String, detail: String, isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 6.dp, bottom = if (isLast) 10.dp else 6.dp)) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(18.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (dragging == value) valueLabel else dragging.toInt().toString(),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onCommit(dragging) },
            valueRange = range,
        )
    }
}
