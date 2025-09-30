package com.jascanner.compression.presentation

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jascanner.compression.domain.model.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedCompressionScreen(
    docId: Long,
    pageCount: Int,
    estimatedOriginalSize: Long,
    onCancel: () -> Unit,
    viewModel: CompressionViewModel = hiltViewModel()
) {
    var selectedProfile by remember { mutableStateOf(CompressionProfile.BALANCED) }
    var selectedFormat by remember { mutableStateOf(OutputFormat.PDF) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // General settings
    var enableAdaptiveBinarization by remember { mutableStateOf(true) }
    var enableDenoise by remember { mutableStateOf(true) }
    var maintainPdfACompliance by remember { mutableStateOf(true) }
    var colorMode by remember { mutableStateOf(ColorMode.AUTO) }

    // Format-specific settings
    var jpegProgressive by remember { mutableStateOf(false) }
    var pngInterlaced by remember { mutableStateOf(false) }
    var webpLossless by remember { mutableStateOf(false) }
    var pdfLinearize by remember { mutableStateOf(true) }
    var pdfOptimizeImages by remember { mutableStateOf(true) }

    val compressionState by viewModel.compressionState.collectAsState()

    LaunchedEffect(compressionState) {
        if (compressionState is CompressionState.Success) {
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Settings") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val settings = CompressionSettings(
                                profile = selectedProfile,
                                outputFormat = selectedFormat,
                                enableAdaptiveBinarization = enableAdaptiveBinarization,
                                enableDenoise = enableDenoise,
                                maintainPdfACompliance = maintainPdfACompliance,
                                colorMode = colorMode
                            )
                            val formatSettings = FormatSpecificSettings(
                                jpegProgressive = jpegProgressive,
                                pngInterlaced = pngInterlaced,
                                webpLossless = webpLossless,
                                pdfLinearize = pdfLinearize,
                                pdfOptimizeImages = pdfOptimizeImages
                            )
                            viewModel.compressAndExport(docId, settings, formatSettings)
                        },
                        enabled = compressionState !is CompressionState.Processing
                    ) {
                        Text("Export")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DocumentInfoCard(
                    pageCount = pageCount,
                    estimatedSize = estimatedOriginalSize,
                    selectedProfile = selectedProfile,
                    selectedFormat = selectedFormat
                )
                Divider()
                Text("Output Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FormatSelector(selectedFormat, { selectedFormat = it }, pageCount)
                Divider()
                Text("Quality Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.selectableGroup()) {
                    CompressionProfile.values().forEach { profile ->
                        ProfileCard(profile, selectedProfile == profile) { selectedProfile = profile }
                    }
                }
                Divider()
                OutlinedButton(
                    onClick = { showAdvancedSettings = !showAdvancedSettings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showAdvancedSettings) "Hide Advanced Settings" else "Show Advanced Settings")
                }

                AnimatedVisibility(visible = showAdvancedSettings) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Advanced Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SwitchRow("Adaptive Binarization", "Enhance text clarity", enableAdaptiveBinarization) { enableAdaptiveBinarization = it }
                        SwitchRow("Denoise", "Remove background noise", enableDenoise) { enableDenoise = it }
                        Text("Color Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        ColorModeChips(selectedMode = colorMode, onModeSelected = { colorMode = it })

                        when (selectedFormat) {
                            OutputFormat.JPG -> SwitchRow("Progressive JPEG", "Web-optimized", jpegProgressive) { jpegProgressive = it }
                            OutputFormat.PNG -> SwitchRow("Interlaced PNG", "Progressive loading", pngInterlaced) { pngInterlaced = it }
                            OutputFormat.WEBP -> SwitchRow("Lossless WebP", "No quality loss", webpLossless) { webpLossless = it }
                            OutputFormat.PDF -> {
                                SwitchRow("Optimize Images", "Compress embedded images", pdfOptimizeImages) { pdfOptimizeImages = it }
                                SwitchRow("PDF/A Compliance", "Archival standard", maintainPdfACompliance) { maintainPdfACompliance = it }
                            }
                        }
                    }
                }
            }
            if (compressionState is CompressionState.Processing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            (compressionState as? CompressionState.Error)?.let {
                SnackbarHost(
                    hostState = remember { SnackbarHostState() }
                        .apply { LaunchedEffect(it) { showSnackbar(it.message) } },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun DocumentInfoCard(pageCount: Int, estimatedSize: Long, selectedProfile: CompressionProfile, selectedFormat: OutputFormat) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Document Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("$pageCount page${if (pageCount > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            }
            Divider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Estimated Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selectedProfile.estimatedSizePerPage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(selectedFormat.extension.uppercase(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FormatSelector(selectedFormat: OutputFormat, onFormatSelected: (OutputFormat) -> Unit, pageCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputFormat.values().forEach { format ->
                val enabled = format.supportsMultiPage || pageCount == 1
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { if (enabled) onFormatSelected(format) },
                    label = { Text(format.displayName) },
                    enabled = enabled,
                    leadingIcon = if (selectedFormat == format) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null
                )
            }
        }
        if (!selectedFormat.supportsMultiPage && pageCount > 1) {
            Text("⚠️ Only the first page will be exported as ${selectedFormat.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfileCard(profile: CompressionProfile, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("${profile.maxDpi} DPI")
                    Chip(profile.estimatedSizePerPage)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ColorModeChips(selectedMode: ColorMode, onModeSelected: (ColorMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorMode.values().forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange)
    }
}