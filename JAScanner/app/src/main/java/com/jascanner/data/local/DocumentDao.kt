package com.jascanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jascanner.data.local.entities.DocumentEntity
import com.jascanner.data.local.entities.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    // Document Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    // Page Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocument(documentId: String): List<PageEntity>
}