package com.jascanner.data.dao

import androidx.room.*
import com.jascanner.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert suspend fun insert(doc: DocumentEntity): Long
    @Update suspend fun update(doc: DocumentEntity)
    @Delete suspend fun delete(doc: DocumentEntity)
    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC") fun getAllDocuments(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id") fun getById(id: Long): Flow<DocumentEntity?>
}

