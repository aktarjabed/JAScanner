package aktarjabed.com.jaascanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query(
        "SELECT id, name, fileName, pageCount, sizeBytes, createdAt, updatedAt, favorite, folderId, source, pinned, " +
            "(length(ocrText) > 0) AS hasText FROM documents ORDER BY pinned DESC, updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<DocumentEntity>

    // searchText is ocrText normalised with NFKC + lower-case, because SQLite's LIKE only folds
    // ASCII case. ESCAPE lets the caller search for a literal %, _ or \ by escaping it first (see
    // DocumentRepository.idsMatchingText) instead of those characters being SQL LIKE wildcards.
    @Query("SELECT id FROM documents WHERE searchText LIKE '%' || :query || '%' ESCAPE '\\'")
    suspend fun idsMatchingText(query: String): List<Long>

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("UPDATE documents SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?)

    @Query("UPDATE documents SET ocrText = :text, searchText = :searchText WHERE id = :id")
    suspend fun setOcrText(id: Long, text: String, searchText: String)

    @Query("UPDATE documents SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE documents SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE documents SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM documents")
    suspend fun totalSize(): Long
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM folders WHERE id = :id")
    suspend fun countWithId(id: Long): Int

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    /** Subfolders of a deleted folder become top-level folders instead of disappearing. */
    @Query("UPDATE folders SET parentId = NULL WHERE parentId = :folderId")
    suspend fun detachChildren(folderId: Long)

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderOf(folderId: Long)
}
