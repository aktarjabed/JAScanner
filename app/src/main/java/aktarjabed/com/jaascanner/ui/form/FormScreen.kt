package aktarjabed.com.jaascanner.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aktarjabed.com.jaascanner.container
import aktarjabed.com.jaascanner.data.ShareHelper
import aktarjabed.com.jaascanner.pdf.FormField
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.ui.AppViewModel
import aktarjabed.com.jaascanner.ui.components.SectionTitle
import aktarjabed.com.jaascanner.ui.containerViewModel
import aktarjabed.com.jaascanner.ui.nav.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(app: AppViewModel, documentId: Long) {
    val context = LocalContext.current
    val container = context.container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val vm = containerViewModel { FormViewModel(it, documentId) }
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler { app.pop() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Fill in form") },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Fill in the fields below and save a copy. Text boxes, check boxes, radio buttons and " +
                        "choice lists can be filled in; other field types are shown but cannot be changed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            if (!state.loading && state.fields.isEmpty()) {
                item {
                    Text(
                        state.message ?: "This PDF has no fillable fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item { SectionTitle("Fields") }
            items(state.fields, key = { it.name }) { field ->
                FieldRow(
                    field = field,
                    value = state.values[field.name] ?: field.value,
                    onText = { vm.setText(field.name, it) },
                    onChecked = { vm.setChecked(field.name, it) },
                    onChoice = { vm.setChoice(field.name, it) },
                )
            }

            if (state.fields.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Flatten", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Bake the values into the page so they cannot be edited again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.flatten, onCheckedChange = vm::setFlatten)
                    }
                    Button(
                        onClick = vm::save,
                        enabled = !state.running && state.document != null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text(if (state.running) "Saving..." else "Save filled copy") }
                }
            }

            state.message?.let { message ->
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            state.result?.let { saved ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { app.replaceTop(Screen.Viewer(saved.id)) }) { Text("Open") }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            try {
                                                ShareHelper.sharePdf(context, container.documents.fileOf(saved), saved.name)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                container.messenger.post(error.toUserMessage())
                                            }
                                        }
                                    }) { Text("Share") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: FormField,
    value: String,
    onText: (String) -> Unit,
    onChecked: (Boolean) -> Unit,
    onChoice: (String) -> Unit,
) {
    when (field.kind) {
        FormField.Kind.Text -> OutlinedTextField(
            value = value,
            onValueChange = onText,
            label = { Text(field.name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FormField.Kind.Checkbox -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value == "true", onCheckedChange = onChecked)
            Text(field.name, modifier = Modifier.padding(start = 8.dp))
        }
        FormField.Kind.Radio -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(field.name, style = MaterialTheme.typography.labelLarge)
            field.options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChoice(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == option, onClick = null)
                    Text(option, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                }
            }
        }
        FormField.Kind.Choice -> {
            var open by remember { mutableStateOf(false) }
            val shown = field.optionLabels.getOrNull(field.options.indexOf(value)) ?: "Choose..."
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(field.name, style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { open = true }) { Text(shown) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        field.options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(field.optionLabels.getOrElse(index) { option }) },
                                onClick = {
                                    open = false
                                    onChoice(option)
                                },
                            )
                        }
                    }
                }
            }
        }
        FormField.Kind.Unsupported -> Text(
            "${field.name} (not editable in this version)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
