package aktarjabed.com.jaascanner.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One fillable field of a PDF form. Only text boxes and check boxes can be filled in this version. */
data class FormField(
    val name: String,
    val kind: Kind,
    val value: String,
    /** Values that can be chosen (radio buttons and drop-downs); what is written into the PDF. */
    val options: List<String> = emptyList(),
    /** What the user sees for each option; same size as [options]. */
    val optionLabels: List<String> = options,
) {
    enum class Kind { Text, Checkbox, Radio, Choice, Unsupported }
}

/** Reads and fills AcroForm fields. Every write goes to a new file; the source is never changed. */
object PdfForms {

    suspend fun listFields(source: File): List<FormField> = withContext(Dispatchers.IO) {
        PDDocument.load(source).use { document ->
            val form = document.documentCatalog.acroForm ?: return@withContext emptyList()
            val fields = mutableListOf<FormField>()
            val iterator = form.fieldIterator
            while (iterator.hasNext()) {
                val field: PDField = iterator.next()
                val name = field.fullyQualifiedName ?: continue
                if (name.isBlank()) continue
                fields.add(
                    when (field) {
                        is PDTextField -> FormField(name, FormField.Kind.Text, field.value ?: "")
                        is PDCheckBox -> FormField(name, FormField.Kind.Checkbox, if (field.isChecked) "true" else "false")
                        is PDRadioButton -> {
                            val options = field.onValues.toList().sorted()
                            if (options.isEmpty()) {
                                FormField(name, FormField.Kind.Unsupported, "")
                            } else {
                                FormField(name, FormField.Kind.Radio, field.value ?: "Off", options)
                            }
                        }
                        is PDChoice -> {
                            val export = field.optionsExportValues.orEmpty()
                            val shown = field.optionsDisplayValues.orEmpty()
                            if (field.isMultiSelect || export.isEmpty()) {
                                FormField(name, FormField.Kind.Unsupported, field.valueAsString ?: "")
                            } else {
                                val labels = if (shown.size == export.size) shown else export
                                FormField(name, FormField.Kind.Choice, field.value.firstOrNull() ?: "", export, labels)
                            }
                        }
                        else -> FormField(name, FormField.Kind.Unsupported, field.valueAsString ?: "")
                    },
                )
            }
            fields
        }
    }

    /**
     * Writes [values] (field name to new value) into a copy of [source]. Checkboxes take "true" or "false".
     * With [flatten] the field appearances are baked into the page, so the values can no longer be edited.
     */
    suspend fun fill(source: File, target: File, values: Map<String, String>, flatten: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(source).use { document ->
                    val form = document.documentCatalog.acroForm
                        ?: throw PdfToolException("This PDF has no fillable fields.")
                    for ((name, value) in values) {
                        when (val field = form.getField(name)) {
                            is PDTextField -> field.setValue(value)
                            is PDCheckBox -> if (value == "true") field.check() else field.unCheck()
                            is PDRadioButton -> if (value.isNotEmpty() && value != "Off") field.setValue(value)
                            is PDChoice -> if (value.isNotEmpty()) field.setValue(value)
                            else -> Unit
                        }
                    }
                    if (flatten) form.flatten()
                    document.save(target)
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }
}
