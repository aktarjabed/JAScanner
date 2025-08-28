package com.jascanner.data.dao

import androidx.room.*
import com.jascanner.data.entities.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert suspend fun insert(s: ScanSessionEntity): Long
    @Query("DELETE FROM scan_sessions WHERE id = :id") suspend fun deleteById(id: Long)
    @Update suspend fun update(s: ScanSessionEntity)
    @Query("SELECT * FROM scan_sessions ORDER BY createdAt DESC") fun getAll(): Flow<List<ScanSessionEntity>>
    @Query("SELECT * FROM scan_sessions WHERE id = :id") fun getById(id: Long): Flow<ScanSessionEntity?>
}

