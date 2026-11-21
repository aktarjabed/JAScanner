package aktarjabed.com.jaascanner.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import aktarjabed.com.jaascanner.core.Formatters
import aktarjabed.com.jaascanner.data.db.AppDatabase
import aktarjabed.com.jaascanner.data.db.DocumentDao
import aktarjabed.com.jaascanner.data.db.DocumentEntity
import aktarjabed.com.jaascanner.data.db.DocumentSummary
import aktarjabed.com.jaascanner.data.db.FolderDao
import aktarjabed.com.jaascanner.data.db.FolderEntity
import aktarjabed.com.jaascanner.pdf.PdfPageRenderer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Owns the document files (filesDir/documents) and their database rows. */
class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao,
) {
    private val dir = File(context.filesDir, "documents").apply { mkdirs() }

    val summaries: Flow<List<DocumentSummary>> = documentDao.observeSummaries()
    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()

    fun fileOf(document: DocumentEntity): File = File(dir, document.fileName)

    fun fileOf(summary: DocumentSummary): File = File(dir, summary.fileName)

    /** A fresh, not yet registered output file inside the documents folder. */
    fun newOutputFile(): File = File(dir, UUID.randomUUID().toString() + ".pdf")

    suspend fun get(id: Long): DocumentEntity? = documentDao.get(id)

    suspend fun getMany(ids: List<Long>): List<DocumentEntity> =
        if (ids.isEmpty()) emptyList() else documentDao.getMany(ids)

    /** Searches library text for [query] literally: a user typing a real `%` or `_` must match
     *  that character, not use it as a SQL LIKE wildcard, so both are escaped before the query
     *  (backslash first, so an escaped character is never re-escaped). */
    suspend fun idsMatchingText(query: String): List<Long> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return documentDao.idsMatchingText(escaped)
    }

    /** Copies a PDF chosen by the user (or opened from another app) into the library. */
    suspend fun importFromUri(uri: Uri, name: String?, source: String, folderId: Long? = null): DocumentEntity? =
        withContext(Dispatchers.IO) {
            val target = newOutputFile()
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
                if (!looksLikePdf(target)) {
                    target.delete()
                    return@withContext null
                }
                val displayName = name ?: queryDisplayName(uri) ?: Formatters.defaultScanName(System.currentTimeMillis())
                registerOutput(target, Formatters.stripPdfExtension(displayName), source, folderId)
            } catch (error: CancellationException) {
                target.delete()
                throw error
            } catch (error: Exception) {
                target.delete()
                null
            }
        }

    /** Registers a PDF that already sits in the documents folder. */
    suspend fun registerOutput(
        file: File,
        name: String,
        source: String,
        folderId: Long? = null,
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val draft = DocumentEntity(
            name = name,
            fileName = file.name,
            pageCount = PdfPageRenderer.pageCountOrZero(file),
            sizeBytes = file.length(),
            createdAt = now,
            updatedAt = now,
            folderId = folderId,
            source = source,
        )
        draft.copy(id = documentDao.insert(draft))
    }

    /**
     * Swaps the content of [document] for [newFile] (a PDF already inside the documents folder), for
     * small changes such as a bookmark. The new file must open and keep the page count; otherwise it
     * is deleted and the original is left exactly as it was. Returns the updated row, or null.
     */
    suspend fun replaceContent(document: DocumentEntity, newFile: File): DocumentEntity? =
        withContext(Dispatchers.IO) {
            val pages = PdfPageRenderer.pageCountOrZero(newFile)
            if (pages == 0 || (document.pageCount > 0 && pages != document.pageCount)) {
                newFile.delete()
                return@withContext null
            }
            val target = fileOf(document)
            if (!newFile.renameTo(target)) {
                newFile.delete()
                return@withContext null
            }
            val updated = document.copy(sizeBytes = target.length(), updatedAt = System.currentTimeMillis())
            documentDao.update(updated)
            updated
        }

    suspend fun rename(id: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isNotEmpty()) documentDao.rename(id, clean, System.currentTimeMillis())
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = documentDao.setFavorite(id, favorite)

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) {
        ids.forEach { documentDao.setPinned(it, pinned) }
    }

    suspend fun setOcrText(id: Long, text: String) = documentDao.setOcrText(id, text)

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) {
        if (ids.isNotEmpty()) documentDao.moveToFolder(ids, folderId)
    }

    suspend fun delete(ids: List<Long>) = withContext(Dispatchers.IO) {
        getMany(ids).forEach { document ->
            fileOf(document).delete()
            documentDao.delete(document)
        }
    }

    suspend fun createFolder(name: String, parentId: Long? = null): Long =
        folderDao.insert(FolderEntity(name = name.trim(), parentId = parentId))

    suspend fun folderExists(id: Long): Boolean = folderDao.countWithId(id) > 0

    suspend fun renameFolder(id: Long, name: String) = folderDao.rename(id, name.trim())

    suspend fun deleteFolder(id: Long) {
        // All three steps together, so a process death mid-delete never leaves documents or
        // subfolders detached from a folder row that was itself only half-removed.
        database.withTransaction {
            folderDao.clearFolderOf(id)
            folderDao.detachChildren(id)
            folderDao.delete(id)
        }
    }

    suspend fun count(): Int = documentDao.count()

    suspend fun totalSize(): Long = documentDao.totalSize()

    private fun looksLikePdf(file: File): Boolean = try {
        file.inputStream().use { stream ->
            val header = ByteArray(1_024)
            val read = stream.read(header)
            read > 4 && String(header, 0, read, Charsets.ISO_8859_1).contains("%PDF-")
        }
    } catch (error: Exception) {
        false
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (error: Exception) {
        null
    }
}
