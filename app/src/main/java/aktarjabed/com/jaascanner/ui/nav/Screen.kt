package aktarjabed.com.jaascanner.ui.nav

enum class ToolKind(val title: String, val description: String) {
    Merge("Merge PDFs", "Combine several documents into one"),
    ImagesToPdf("Images to PDF", "Turn photos and pictures into a PDF"),
    Extract("Extract pages", "Keep only the pages you choose"),
    Split("Split PDF", "Cut into equal parts or into named page ranges"),
    Rotate("Rotate pages", "Fix sideways or upside-down pages"),
    Reorder("Reorder pages", "Put the pages in a new order"),
    InsertBlank("Insert blank pages", "Add empty pages at any position"),
    InsertFromPdf("Insert pages from a PDF", "Place pages of another document into this one"),
    Crop("Trim margins", "Cut a border off every chosen page"),
    DeletePages("Delete pages", "Remove the pages you do not need"),
    Compress("Compress PDF", "Make a large scan smaller"),
    Protect("Add password", "Lock a PDF with a password"),
    Unlock("Remove password", "Save an unlocked copy (you must know the password)"),
    Watermark("Watermark", "Stamp text diagonally across every page"),
    ExportImages("PDF to images", "Share or save pages as JPEG pictures"),
}

/** Gives every screen instance its own id, so each gets its own ViewModels and saved state. */
object ScreenIds {
    private var last = 0L

    fun next(): Long {
        last += 1
        return last
    }
}

sealed interface Screen {
    val id: Long

    data object Home : Screen {
        override val id: Long = 0L
    }

    data class Viewer(val documentId: Long, override val id: Long = ScreenIds.next()) : Screen

    data class Editor(val documentId: Long, override val id: Long = ScreenIds.next()) : Screen

    data class Ocr(val documentId: Long?, override val id: Long = ScreenIds.next()) : Screen

    data class Tool(
        val kind: ToolKind,
        val preselectedIds: List<Long> = emptyList(),
        override val id: Long = ScreenIds.next(),
    ) : Screen

    data class Qr(override val id: Long = ScreenIds.next()) : Screen

    data class Cards(override val id: Long = ScreenIds.next()) : Screen

    data class Form(val documentId: Long, override val id: Long = ScreenIds.next()) : Screen

    /** Page thumbnails: drag to reorder, rotate, delete, duplicate. */
    data class Pages(val documentId: Long, override val id: Long = ScreenIds.next()) : Screen

    /** Reflowed reading view with read-aloud. */
    data class Reader(val documentId: Long, override val id: Long = ScreenIds.next()) : Screen
}
