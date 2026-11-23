package aktarjabed.com.jaascanner.core

import java.io.File

/** One picture taken by the in-app camera, waiting to become a PDF page. */
data class CapturedPage(val file: File)

/**
 * The pages captured in one camera session. Kept out of Compose so the add/remove/limit rules can
 * be unit-tested without a camera: the screen only renders [count] and asks for [snapshot] when
 * the user taps Done.
 */
class ScanSession(val maxPages: Int = 200) {

    private val pages = mutableListOf<CapturedPage>()

    val count: Int get() = pages.size
    val isEmpty: Boolean get() = pages.isEmpty()
    val isFull: Boolean get() = pages.size >= maxPages

    /** Adds a captured picture; returns false (and the caller deletes it) once the limit is reached. */
    fun add(page: CapturedPage): Boolean {
        if (isFull) return false
        pages += page
        return true
    }

    /** Removes the most recent page, returning it so the caller can delete its file. */
    fun removeLast(): CapturedPage? = if (pages.isEmpty()) null else pages.removeAt(pages.lastIndex)

    /** A copy, so saving cannot be changed by a picture arriving at the same moment. */
    fun snapshot(): List<CapturedPage> = pages.toList()

    /** Deletes every captured file. Used when the session is finished or abandoned. */
    fun clear() {
        pages.forEach { it.file.delete() }
        pages.clear()
    }
}
