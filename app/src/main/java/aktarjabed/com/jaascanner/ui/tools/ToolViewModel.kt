package aktarjabed.com.jaascanner.ui.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.core.PageGroups
import aktarjabed.com.jaascanner.core.PageGroupsResult
import aktarjabed.com.jaascanner.core.PageOrder
import aktarjabed.com.jaascanner.core.PageSelection
import aktarjabed.com.jaascanner.core.PageSelectionResult
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.data.db.DocumentSummary
import aktarjabed.com.jaascanner.pdf.BitmapImageRecoder
import aktarjabed.com.jaascanner.pdf.ImageDecoding
import aktarjabed.com.jaascanner.pdf.ImageFilter
import aktarjabed.com.jaascanner.pdf.PageImageExporter
import aktarjabed.com.jaascanner.pdf.PageSizeOption
import aktarjabed.com.jaascanner.pdf.PdfEditor
import aktarjabed.com.jaascanner.pdf.PdfImageCompress
import aktarjabed.com.jaascanner.pdf.PdfTools
import aktarjabed.com.jaascanner.pdf.PdfToolException
import aktarjabed.com.jaascanner.pdf.TextBitmaps
import aktarjabed.com.jaascanner.pdf.toUserMessage
import aktarjabed.com.jaascanner.ui.nav.ToolKind
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How [ToolKind.Compress] should shrink the file. */
enum class CompressMode { KeepText, Smallest }

/** What [ToolKind.Watermark] stamps onto the page. */
enum class WatermarkMode { Text, Picture }

data class ToolUiState(
    val documents: List<DocumentSummary> = emptyList(),
    val selected: List<Long> = emptyList(),
    val images: List<Uri> = emptyList(),
    val imageFilter: ImageFilter = ImageFilter.Original,
    val pageText: String = "",
    val perFile: String = "5",
    val splitByRanges: Boolean = false,
    val rangesText: String = "",
    val orderText: String = "",
    val insertAfter: String = "0",
    val insertSource: Long? = null,
    val blankCount: String = "1",
    val cropPercent: Int = 5,
    val degrees: Int = 90,
    val password: String = "",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkMode: WatermarkMode = WatermarkMode.Text,
    val watermarkImage: Uri? = null,
    val watermarkTiled: Boolean = false,
    val opacity: Float = 0.25f,
    val compressMode: CompressMode = CompressMode.KeepText,
    val name: String = "",
    val running: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
    val results: List<DocumentEntity> = emptyList(),
    val exported: List<File> = emptyList(),
)

private data class ToolOutcome(
    val results: List<DocumentEntity> = emptyList(),
    val exported: List<File> = emptyList(),
    val message: String,
)

class ToolViewModel(
    private val container: AppContainer,
    val kind: ToolKind,
    preselected: List<Long>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ToolUiState(selected = preselected))
    val state: StateFlow<ToolUiState> = mutableState.asStateFlow()

    val allowsManyDocuments: Boolean get() = kind == ToolKind.Merge

    init {
        viewModelScope.launch {
            container.documents.summaries.collect { docs -> mutableState.update { it.copy(documents = docs) } }
        }
    }

    fun toggleDocument(id: Long) {
        mutableState.update { current ->
            val next = when {
                !allowsManyDocuments -> listOf(id)
                id in current.selected -> current.selected - id
                else -> current.selected + id
            }
            current.copy(selected = next)
        }
    }

    fun addImages(uris: List<Uri>) = mutableState.update { it.copy(images = it.images + uris) }

    fun setImageFilter(value: ImageFilter) = mutableState.update { it.copy(imageFilter = value) }

    fun removeImage(index: Int) = mutableState.update { current ->
        if (index !in current.images.indices) current else current.copy(images = current.images.filterIndexed { i, _ -> i != index })
    }

    /** Moves a picture one place earlier (-1) or later (+1) in the page order. */
    fun moveImage(index: Int, delta: Int) = mutableState.update { current ->
        val target = index + delta
        if (index !in current.images.indices || target !in current.images.indices) {
            current
        } else {
            val list = current.images.toMutableList()
            list.add(target, list.removeAt(index))
            current.copy(images = list)
        }
    }

    fun clearImages() = mutableState.update { it.copy(images = emptyList()) }

    fun setPageText(value: String) = mutableState.update { it.copy(pageText = value) }

    fun setPerFile(value: String) = mutableState.update { it.copy(perFile = value.filter(Char::isDigit).take(4)) }

    fun setSplitByRanges(value: Boolean) = mutableState.update { it.copy(splitByRanges = value) }

    fun setRangesText(value: String) = mutableState.update { it.copy(rangesText = value.take(200)) }

    fun setOrderText(value: String) = mutableState.update { it.copy(orderText = value.take(300)) }

    fun setInsertAfter(value: String) = mutableState.update { it.copy(insertAfter = value.filter(Char::isDigit).take(5)) }

    fun setInsertSource(id: Long) = mutableState.update { it.copy(insertSource = id) }

    fun setBlankCount(value: String) = mutableState.update { it.copy(blankCount = value.filter(Char::isDigit).take(3)) }

    fun setCropPercent(value: Int) = mutableState.update { it.copy(cropPercent = value.coerceIn(1, 30)) }

    fun setDegrees(value: Int) = mutableState.update { it.copy(degrees = value) }

    fun setPassword(value: String) = mutableState.update { it.copy(password = value) }

    fun setPageSize(value: PageSizeOption) = mutableState.update { it.copy(pageSize = value) }

    fun setWatermarkText(value: String) = mutableState.update { it.copy(watermarkText = value.take(60)) }

    fun setOpacity(value: Float) = mutableState.update { it.copy(opacity = value) }

    fun setWatermarkMode(value: WatermarkMode) = mutableState.update { it.copy(watermarkMode = value) }

    fun setWatermarkImage(uri: Uri?) = mutableState.update { it.copy(watermarkImage = uri) }

    fun setWatermarkTiled(value: Boolean) = mutableState.update { it.copy(watermarkTiled = value) }

    fun setCompressMode(value: CompressMode) = mutableState.update { it.copy(compressMode = value) }

    fun setName(value: String) = mutableState.update { it.copy(name = value) }

    private fun pageCountOf(s: ToolUiState): Int =
        s.documents.firstOrNull { it.id == s.selected.firstOrNull() }?.pageCount ?: 0

    private fun pageCountOfDocument(s: ToolUiState, id: Long?): Int =
        s.documents.firstOrNull { it.id == id }?.pageCount ?: 0

    /** Inline error for a page-list field. Blank is valid only when it means "all pages". */
    fun pageError(
        s: ToolUiState,
        text: String = s.pageText,
        allowBlank: Boolean,
        pageCount: Int = pageCountOf(s),
    ): String? {
        if (text.isBlank()) return if (allowBlank) null else "Enter at least one page."
        if (pageCount <= 0) return null // the tool itself reports a locked/unreadable document
        return when (val parsed = PageSelection.parse(text, pageCount)) {
            is PageSelectionResult.Ok -> null
            is PageSelectionResult.Error -> parsed.message
        }
    }

    /** "Pages to insert" belongs to the source PDF, not to the document being edited. */
    fun insertPageError(s: ToolUiState): String? = pageError(
        s,
        allowBlank = true,
        pageCount = pageCountOfDocument(s, s.insertSource),
    )

    fun rangesError(s: ToolUiState): String? {
        if (s.rangesText.isBlank()) return "Enter at least one page range."
        val count = pageCountOf(s)
        if (count <= 0) return null
        return when (val parsed = PageGroups.parse(s.rangesText, count)) {
            is PageGroupsResult.Ok -> null
            is PageGroupsResult.Error -> parsed.message
        }
    }

    fun orderError(s: ToolUiState): String? {
        if (s.orderText.isBlank()) return "Enter the new page order."
        val count = pageCountOf(s)
        if (count <= 0) return null
        return when (val parsed = PageOrder.parse(s.orderText, count)) {
            is PageSelectionResult.Ok -> null
            is PageSelectionResult.Error -> parsed.message
        }
    }

    fun perFileError(s: ToolUiState): String? =
        if ((s.perFile.toIntOrNull() ?: 0) >= 1) null else "Enter 1 or more pages per file."

    fun blankCountError(s: ToolUiState): String? =
        if ((s.blankCount.toIntOrNull() ?: 0) >= 1) null else "Enter 1 or more blank pages."

    fun insertAfterError(s: ToolUiState): String? {
        val page = s.insertAfter.toIntOrNull() ?: return "Enter a page number (0 means at the start)."
        val count = pageCountOf(s)
        return if (count <= 0 || page in 0..count) null else "Page $page does not exist. This document has $count pages."
    }

    fun passwordError(s: ToolUiState): String? = when (kind) {
        ToolKind.Protect -> if (s.password.length >= 4) null else "Use at least 4 characters."
        ToolKind.Unlock -> if (s.password.isNotEmpty()) null else "Enter the current password."
        else -> null
    }

    fun watermarkError(s: ToolUiState): String? = when (s.watermarkMode) {
        WatermarkMode.Text -> if (s.watermarkText.isNotBlank()) null else "Enter the watermark text."
        WatermarkMode.Picture -> if (s.watermarkImage != null) null else "Choose a picture for the watermark."
    }

    /** First reason why this tool cannot run, or null when all inputs are valid. */
    fun validationMessage(s: ToolUiState): String? {
        if (s.running) return null
        if (kind == ToolKind.ImagesToPdf) {
            if (s.images.isEmpty()) return "Pick at least one picture."
        } else if (kind == ToolKind.Merge) {
            if (s.selected.size < 2) return "Choose at least two documents."
        } else if (s.selected.size != 1) {
            return "Choose a document first."
        }
        return when (kind) {
            ToolKind.Extract, ToolKind.DeletePages -> pageError(s, allowBlank = false)
            ToolKind.ExportImages, ToolKind.Rotate, ToolKind.Crop -> pageError(s, allowBlank = true)
            ToolKind.InsertFromPdf -> insertPageError(s)
                ?: insertAfterError(s)
                ?: if (s.insertSource == null) "Choose the document to take pages from." else null
            ToolKind.Split -> if (s.splitByRanges) rangesError(s) else perFileError(s)
            ToolKind.Reorder -> orderError(s)
            ToolKind.InsertBlank -> blankCountError(s) ?: insertAfterError(s)
            ToolKind.Protect, ToolKind.Unlock -> passwordError(s)
            ToolKind.Watermark -> watermarkError(s)
            else -> null
        }
    }

    fun canRun(s: ToolUiState): Boolean = !s.running && validationMessage(s) == null

    /** Runs in the app scope so the result is saved even if the user leaves this screen. */
    fun run(context: Context) {
        val snapshot = mutableState.value
        val reason = validationMessage(snapshot)
        if (reason != null) {
            mutableState.update { it.copy(message = reason) }
            return
        }
        mutableState.update {
            it.copy(running = true, message = null, results = emptyList(), exported = emptyList(), progress = null)
        }
        val appContext = context.applicationContext
        val outputs = mutableListOf<File>()
        val registered = mutableSetOf<String>()
        container.appScope.launch {
            try {
                val outcome = execute(appContext, snapshot, outputs, registered)
                mutableState.update {
                    it.copy(
                        running = false,
                        progress = null,
                        results = outcome.results,
                        exported = outcome.exported,
                        message = outcome.message,
                    )
                }
            } catch (error: Throwable) {
                mutableState.update { it.copy(running = false, progress = null, message = error.toUserMessage()) }
            } finally {
                // A tool that fails between newOutputFile() and registerOutput() used to leave a
                // zero-byte PDF forever. Files that did get a database row are kept.
                outputs.filterNot { it.name in registered }.forEach { it.delete() }
            }
        }
    }

    private fun setProgress(done: Int, total: Int) {
        mutableState.update { it.copy(progress = done to total) }
    }

    private fun nameOr(s: ToolUiState, fallback: String): String = s.name.trim().ifBlank { fallback }

    private fun pagesOf(text: String, document: DocumentEntity, blankMeansAll: Boolean): List<Int> {
        if (document.pageCount == 0) {
            throw PdfToolException("This PDF cannot be read. If it is password-protected, remove the password first.")
        }
        if (text.isBlank() && blankMeansAll) return (0 until document.pageCount).toList()
        return when (val parsed = PageSelection.parse(text, document.pageCount)) {
            is PageSelectionResult.Ok -> parsed.pages
            is PageSelectionResult.Error -> throw PdfToolException(parsed.message)
        }
    }

    private suspend fun execute(
        context: Context,
        s: ToolUiState,
        outputs: MutableList<File>,
        registered: MutableSet<String>,
    ): ToolOutcome {
        val repo = container.documents
        val settings = container.settings.settings.first()

        fun newOutput(): File = repo.newOutputFile().also { outputs.add(it) }

        suspend fun saveOutput(target: File, name: String, folderId: Long? = null): DocumentEntity {
            val saved = repo.registerOutput(target, name, DocumentEntity.SOURCE_TOOL, folderId)
            registered.add(target.name)
            return saved
        }

        if (kind == ToolKind.ImagesToPdf) {
            val target = newOutput()
            PdfTools.imagesToPdf(context, s.images, s.pageSize, target, s.imageFilter) { done, total -> setProgress(done, total) }
            val saved = saveOutput(
                target,
                nameOr(s, "Images " + Formatters.defaultScanName(System.currentTimeMillis()).removePrefix("Scan ")),
            )
            if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
            return ToolOutcome(listOf(saved), message = "Created a PDF with ${saved.pageCount} pages.")
        }

        val chosen = repo.getMany(s.selected).sortedBy { s.selected.indexOf(it.id) }
        if (chosen.isEmpty()) throw PdfToolException("Choose a document first.")
        val document = chosen.first()
        val file = repo.fileOf(document)

        suspend fun register(target: File, name: String): DocumentEntity =
            saveOutput(target, name, document.folderId)

        return when (kind) {
            ToolKind.Merge -> {
                if (chosen.size < 2) throw PdfToolException("Choose at least two documents.")
                val target = newOutput()
                PdfTools.merge(chosen.map { repo.fileOf(it) }, target)
                val saved = register(target, nameOr(s, "Merged " + chosen.first().name))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Merged ${chosen.size} documents into ${saved.pageCount} pages.")
            }
            ToolKind.Extract -> {
                val pages = pagesOf(s.pageText, document, blankMeansAll = false)
                val target = newOutput()
                PdfTools.extractPages(file, pages, target)
                val saved = register(target, nameOr(s, "${document.name} (pages ${s.pageText.trim()})"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Extracted ${pages.size} pages.")
            }
            ToolKind.DeletePages -> {
                val pages = pagesOf(s.pageText, document, blankMeansAll = false)
                val target = newOutput()
                PdfTools.deletePages(file, pages, target)
                val saved = register(target, nameOr(s, "${document.name} (trimmed)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Deleted ${pages.size} pages. ${saved.pageCount} remain.")
            }
            ToolKind.Rotate -> {
                val pages = pagesOf(s.pageText, document, blankMeansAll = true)
                val target = newOutput()
                PdfTools.rotatePages(file, pages, s.degrees, target)
                val saved = register(target, nameOr(s, "${document.name} (rotated)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Rotated ${pages.size} pages.")
            }
            ToolKind.Split -> {
                if (document.pageCount == 0) throw PdfToolException("This PDF cannot be read.")
                val files = if (s.splitByRanges) {
                    val groups = when (val parsed = PageGroups.parse(s.rangesText, document.pageCount)) {
                        is PageGroupsResult.Ok -> parsed.groups
                        is PageGroupsResult.Error -> throw PdfToolException(parsed.message)
                    }
                    PdfTools.splitRanges(file, groups) { newOutput() }
                } else {
                    PdfTools.splitEvery(file, s.perFile.toIntOrNull() ?: 0) { newOutput() }
                }
                val base = nameOr(s, document.name)
                val saved = files.mapIndexed { index, part -> register(part, "$base - part ${index + 1}") }
                if (settings.autoOcr) saved.forEach { container.ocrIndexer.indexInBackground(it.id) }
                ToolOutcome(saved, message = "Split into ${saved.size} files.")
            }
            ToolKind.Reorder -> {
                if (document.pageCount == 0) throw PdfToolException("This PDF cannot be read.")
                val order = when (val parsed = PageOrder.parse(s.orderText, document.pageCount)) {
                    is PageSelectionResult.Ok -> parsed.pages
                    is PageSelectionResult.Error -> throw PdfToolException(parsed.message)
                }
                val target = newOutput()
                PdfTools.reorderPages(file, order, target)
                val saved = register(target, nameOr(s, "${document.name} (reordered)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Reordered ${order.size} pages.")
            }
            ToolKind.InsertBlank -> {
                if (document.pageCount == 0) throw PdfToolException("This PDF cannot be read.")
                val after = s.insertAfter.toIntOrNull() ?: 0
                val count = s.blankCount.toIntOrNull() ?: 1
                val target = newOutput()
                PdfTools.insertBlankPages(file, after, count, s.pageSize, target)
                val saved = register(target, nameOr(s, "${document.name} (with blank pages)"))
                ToolOutcome(listOf(saved), message = "Inserted $count blank page(s). ${saved.pageCount} pages now.")
            }
            ToolKind.InsertFromPdf -> {
                if (document.pageCount == 0) throw PdfToolException("This PDF cannot be read.")
                val other = repo.get(s.insertSource ?: throw PdfToolException("Choose the document to take pages from."))
                    ?: throw PdfToolException("That document no longer exists.")
                val pages = pagesOf(s.pageText, other, blankMeansAll = true)
                val after = s.insertAfter.toIntOrNull() ?: 0
                val target = newOutput()
                PdfTools.insertFromPdf(file, repo.fileOf(other), pages, after, target)
                val saved = register(target, nameOr(s, "${document.name} (with pages from ${other.name})"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Inserted ${pages.size} pages. ${saved.pageCount} pages now.")
            }
            ToolKind.Crop -> {
                val pages = pagesOf(s.pageText, document, blankMeansAll = true)
                val target = newOutput()
                PdfTools.cropPages(file, pages, s.cropPercent, target)
                val saved = register(target, nameOr(s, "${document.name} (trimmed)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Trimmed ${s.cropPercent}% from the edges of ${pages.size} pages.")
            }
            ToolKind.Compress -> {
                val target = newOutput()
                when (s.compressMode) {
                    CompressMode.KeepText -> {
                        val outcome = PdfImageCompress.compress(
                            source = file,
                            target = target,
                            quality = settings.compressQuality,
                            recoder = BitmapImageRecoder(),
                        ) { done, total -> setProgress(done, total) }
                        if (outcome.picturesFound == 0) {
                            target.delete()
                            throw PdfToolException("This PDF has no pictures to shrink; its text is already as small as it gets.")
                        }
                        if (outcome.newBytes >= outcome.originalBytes) {
                            target.delete()
                            throw PdfToolException(
                                "Compression would not make this file smaller (it is already ${Formatters.fileSize(outcome.originalBytes)}).",
                            )
                        }
                        val saved = register(target, nameOr(s, "${document.name} (compressed)"))
                        ToolOutcome(
                            listOf(saved),
                            message = "Reduced from ${Formatters.fileSize(outcome.originalBytes)} to " +
                                "${Formatters.fileSize(outcome.newBytes)} (${outcome.picturesShrunk} of ${outcome.picturesFound} " +
                                "pictures shrunk). The text stays real, selectable text.",
                        )
                    }
                    CompressMode.Smallest -> {
                        val outcome = PdfTools.compress(file, target, settings.compressQuality) { done, total -> setProgress(done, total) }
                        if (outcome.newBytes >= outcome.originalBytes) {
                            target.delete()
                            throw PdfToolException(
                                "Compression would not make this file smaller (it is already ${Formatters.fileSize(outcome.originalBytes)}).",
                            )
                        }
                        val saved = register(target, nameOr(s, "${document.name} (compressed)"))
                        if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                        ToolOutcome(
                            listOf(saved),
                            message = "Reduced from ${Formatters.fileSize(outcome.originalBytes)} to " +
                                "${Formatters.fileSize(outcome.newBytes)}. Pages became pictures, so text is read again automatically " +
                                "if text recognition is on.",
                        )
                    }
                }
            }
            ToolKind.Protect -> {
                val target = newOutput()
                PdfTools.protect(file, s.password, target)
                val saved = register(target, nameOr(s, "${document.name} (protected)"))
                ToolOutcome(
                    listOf(saved),
                    message = "Saved a password-protected copy. It shows as locked in the library; share it together with the password.",
                )
            }
            ToolKind.Unlock -> {
                val target = newOutput()
                PdfTools.unlock(file, s.password, target)
                val saved = register(target, nameOr(s, "${document.name} (unlocked)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                ToolOutcome(listOf(saved), message = "Saved an unlocked copy.")
            }
            ToolKind.Watermark -> {
                if (document.pageCount == 0) throw PdfToolException("This PDF cannot be read.")
                // Decode the stamp first: a bad picture must not leave an empty output PDF behind.
                val stamp = when (s.watermarkMode) {
                    WatermarkMode.Text -> TextBitmaps.render(s.watermarkText.trim(), WATERMARK_COLOR, textSizePx = 160f, bold = true)
                    WatermarkMode.Picture -> {
                        val uri = s.watermarkImage ?: throw PdfToolException("Choose a picture for the watermark first.")
                        ImageDecoding.decode(context, uri, maxSide = 1200)
                    }
                }
                val target = newOutput()
                try {
                    val widthFraction = when {
                        s.watermarkTiled -> 0.28f
                        s.watermarkMode == WatermarkMode.Picture -> 0.45f
                        else -> 0.75f
                    }
                    val angle = if (s.watermarkMode == WatermarkMode.Text) 45f else 0f
                    PdfEditor.watermark(
                        file,
                        target,
                        stamp,
                        s.opacity,
                        widthFraction = widthFraction,
                        angleDegrees = angle,
                        tiled = s.watermarkTiled,
                    )
                } finally {
                    stamp.recycle()
                }
                val saved = register(target, nameOr(s, "${document.name} (watermarked)"))
                if (settings.autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                val placement = if (s.watermarkTiled) "repeated across" else "stamped on"
                ToolOutcome(listOf(saved), message = "Watermark $placement ${saved.pageCount} pages.")
            }
            ToolKind.ExportImages -> {
                val pages = pagesOf(s.pageText, document, blankMeansAll = true)
                val files = PageImageExporter.renderToFiles(context, file, document.name, pages) { done, total ->
                    setProgress(done, total)
                }
                ToolOutcome(exported = files, message = "Rendered ${files.size} pages as pictures.")
            }
            ToolKind.ImagesToPdf -> throw IllegalStateException("handled above")
        }
    }

    private companion object {
        const val WATERMARK_COLOR = 0xFF616161.toInt()
    }
}
