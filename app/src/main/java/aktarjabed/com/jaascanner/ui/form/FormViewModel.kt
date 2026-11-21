package aktarjabed.com.jaascanner.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.FormField
import aktarjabed.com.jaascanner.pdf.PdfForms
import aktarjabed.com.jaascanner.pdf.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormUiState(
    val document: DocumentEntity? = null,
    val fields: List<FormField> = emptyList(),
    /** Values the user has typed or ticked, keyed by field name. */
    val values: Map<String, String> = emptyMap(),
    val flatten: Boolean = false,
    val loading: Boolean = true,
    val running: Boolean = false,
    val message: String? = null,
    val result: DocumentEntity? = null,
)

class FormViewModel(private val container: AppContainer, private val documentId: Long) : ViewModel() {

    private val mutableState = MutableStateFlow(FormUiState())
    val state: StateFlow<FormUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val document = container.documents.get(documentId)
            if (document == null) {
                mutableState.update { it.copy(loading = false, message = "This document no longer exists.") }
                return@launch
            }
            try {
                val fields = PdfForms.listFields(container.documents.fileOf(document))
                mutableState.update {
                    it.copy(
                        document = document,
                        fields = fields,
                        values = fields.associate { field -> field.name to field.value },
                        loading = false,
                    )
                }
            } catch (error: Throwable) {
                mutableState.update { it.copy(document = document, loading = false, message = error.toUserMessage()) }
            }
        }
    }

    fun setText(name: String, value: String) = mutableState.update { it.copy(values = it.values + (name to value)) }

    fun setChoice(name: String, value: String) = mutableState.update { it.copy(values = it.values + (name to value)) }

    fun setChecked(name: String, checked: Boolean) =
        mutableState.update { it.copy(values = it.values + (name to checked.toString())) }

    fun setFlatten(value: Boolean) = mutableState.update { it.copy(flatten = value) }

    /** Runs in the app scope so the filled copy is saved even if the user leaves this screen. */
    fun save() {
        val snapshot = mutableState.value
        val document = snapshot.document ?: return
        if (snapshot.running) return
        val editable = snapshot.fields.filter { it.kind != FormField.Kind.Unsupported }
        if (editable.isEmpty()) {
            mutableState.update { it.copy(message = "This PDF has no fields this version can fill.") }
            return
        }
        mutableState.update { it.copy(running = true, message = null, result = null) }
        container.appScope.launch {
            val target = container.documents.newOutputFile()
            try {
                val values = editable.associate { field -> field.name to (snapshot.values[field.name] ?: field.value) }
                PdfForms.fill(container.documents.fileOf(document), target, values, snapshot.flatten)
                val saved = container.documents.registerOutput(
                    target,
                    "${document.name} (filled)",
                    DocumentEntity.SOURCE_TOOL,
                    document.folderId,
                )
                mutableState.update {
                    it.copy(running = false, result = saved, message = "Saved a filled copy. The original is unchanged.")
                }
            } catch (error: Throwable) {
                // Unlike ToolViewModel/EditorViewModel, a failed fill used to leave this half-written
                // file behind in the documents folder with no database row ever pointing at it.
                target.delete()
                mutableState.update { it.copy(running = false, message = error.toUserMessage()) }
            }
        }
    }
}
