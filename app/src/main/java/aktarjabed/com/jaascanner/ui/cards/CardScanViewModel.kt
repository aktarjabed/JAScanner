package aktarjabed.com.jaascanner.ui.cards

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import aktarjabed.com.jaascanner.AppContainer
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.pdf.ImageFilter
import aktarjabed.com.jaascanner.pdf.PdfTools
import aktarjabed.com.jaascanner.pdf.toUserMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One side of a card (front, back, ...); [photo] is a private copy of the picture once captured. */
data class CardSide(val label: String, val photo: File? = null)

data class CardScanState(
    val sides: List<CardSide> = defaultSides(2),
    val onePage: Boolean = true,
    val filter: ImageFilter = ImageFilter.Enhanced,
    val saving: Boolean = false,
    /** Set when a camera/gallery picture could not be copied, so the screen can show a real error. */
    val pictureError: String? = null,
)

const val MAX_SIDES = 20

fun sideLabel(index: Int): String = when (index) {
    0 -> "Front"
    1 -> "Back"
    else -> "Side ${index + 1}"
}

fun defaultSides(count: Int): List<CardSide> = List(count) { CardSide(sideLabel(it)) }

class CardScanViewModel(private val container: AppContainer) : ViewModel() {

    private val mutableState = MutableStateFlow(CardScanState())
    val state: StateFlow<CardScanState> = mutableState.asStateFlow()

    fun addSide() = mutableState.update { current ->
        if (current.sides.size >= MAX_SIDES) current else current.copy(sides = current.sides + CardSide(sideLabel(current.sides.size)))
    }

    fun removeLastSide() {
        val current = mutableState.value
        if (current.sides.size <= 1) return
        val removed = current.sides.last().photo
        mutableState.update { it.copy(sides = it.sides.dropLast(1)) }
        removed?.delete()
    }

    fun setOnePage(value: Boolean) = mutableState.update { it.copy(onePage = value) }

    fun setFilter(value: ImageFilter) = mutableState.update { it.copy(filter = value) }

    fun clearSide(index: Int) {
        // Delete the file once the state no longer references it (not inside update {}, which can
        // run more than once under contention — the same lesson as PagesViewModel, see AUDIT.md).
        val old = mutableState.value.sides.getOrNull(index)?.photo
        mutableState.update { current ->
            current.copy(sides = current.sides.mapIndexed { i, side -> if (i == index) side.copy(photo = null) else side })
        }
        old?.delete()
    }

    /** Copies the picture into private storage right away: scanner and picker URIs are short-lived. */
    fun setSide(context: Context, index: Int, uri: Uri) {
        if (index !in mutableState.value.sides.indices) return
        val appContext = context.applicationContext
        mutableState.update { it.copy(pictureError = null) }
        container.appScope.launch {
            val copy = withContext(Dispatchers.IO) {
                val dir = File(appContext.cacheDir, "cards").apply { mkdirs() }
                val file = File(dir, UUID.randomUUID().toString() + ".jpg")
                try {
                    val stream = appContext.contentResolver.openInputStream(uri) ?: error("no stream")
                    stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    file
                } catch (error: CancellationException) {
                    file.delete()
                    throw error
                } catch (error: Exception) {
                    file.delete()
                    null
                }
            }
            if (copy == null) {
                mutableState.update { it.copy(pictureError = "That picture could not be read.") }
            } else if (index !in mutableState.value.sides.indices) {
                // The side was removed while the picture was being copied.
                copy.delete()
            } else {
                // A rescan of a side that already had a photo must not leak the old cache file.
                val previous = mutableState.value.sides[index].photo
                mutableState.update { current ->
                    current.copy(
                        pictureError = null,
                        sides = current.sides.mapIndexed { i, side ->
                            if (i == index) side.copy(photo = copy) else side
                        },
                    )
                }
                previous?.delete()
            }
        }
    }

    fun save(context: Context, onSaved: (Long) -> Unit) {
        val current = mutableState.value
        val photos = current.sides.mapNotNull { it.photo }
        if (photos.isEmpty() || current.saving) {
            if (photos.isEmpty()) container.messenger.post("Capture at least one side first.")
            return
        }
        val appContext = context.applicationContext
        mutableState.update { it.copy(saving = true) }
        container.appScope.launch {
            val target = container.documents.newOutputFile()
            try {
                PdfTools.cardsToPdf(appContext, photos.map { it.toUri() }, current.onePage, current.filter, target)
                val folderId = container.settings.settings.first().defaultFolderId
                    ?.takeIf { container.documents.folderExists(it) }
                val name = "Card " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())
                val saved = container.documents.registerOutput(target, name, DocumentEntity.SOURCE_SCAN, folderId)
                if (container.settings.settings.first().autoOcr) container.ocrIndexer.indexInBackground(saved.id)
                photos.forEach { it.delete() }
                withContext(Dispatchers.Main) { onSaved(saved.id) }
            } catch (error: CancellationException) {
                target.delete()
                photos.forEach { it.delete() }
                throw error
            } catch (error: Throwable) {
                target.delete()
                photos.forEach { it.delete() }
                container.messenger.post(error.toUserMessage())
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        // Pictures that were never saved should not linger in the cache. A save still in flight
        // owns its photos: it deletes them if it fails and consumes them if it succeeds.
        if (mutableState.value.saving) return
        mutableState.value.sides.forEach { it.photo?.delete() }
    }
}
