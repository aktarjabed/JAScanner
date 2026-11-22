package aktarjabed.com.jaascanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentId"])],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Parent folder for subfolders; null for top-level folders. */
    val parentId: Long? = null,
)

@Entity(
    tableName = "documents",
    indices = [Index(value = ["updatedAt"]), Index(value = ["folderId"])],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** File name inside filesDir/documents. */
    val fileName: String,
    /** 0 means the PDF could not be opened (for example it is password protected). */
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    /** Text found by OCR, kept exactly as recognised for display and editing. */
    val ocrText: String = "",
    /**
     * [ocrText] after [aktarjabed.com.jaascanner.core.TextSearch.normalize]. SQLite's LIKE only
     * folds ASCII case, so searches run against this column instead of the original text.
     */
    @ColumnInfo(defaultValue = "") val searchText: String = "",
    val source: String = SOURCE_SCAN,
    /** Pinned documents are listed first. */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
) {
    companion object {
        const val SOURCE_SCAN = "scan"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_TOOL = "tool"
    }
}

/** Lightweight row for the library list: everything except the (possibly large) OCR text. */
data class DocumentSummary(
    val id: Long,
    val name: String,
    val fileName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val folderId: Long?,
    val source: String,
    val hasText: Boolean,
    val pinned: Boolean,
)
