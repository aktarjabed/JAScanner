package com.jascanner.data.dao

import androidx.room.*
import com.jascanner.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' OR full_ocr_text LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity): Int

    @Delete
    suspend fun deleteDocument(document: DocumentEntity): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long): Int

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getTotalCount(): Int

    @Query("SELECT SUM(file_size) FROM documents")
    suspend fun getTotalFileSize(): Long?
}