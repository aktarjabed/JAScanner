package com.jascanner.data.dao

import androidx.room.*
import com.jascanner.data.entities.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    // Reads

    @Query("SELECT * FROM scans WHERE document_id = :documentId ORDER BY page_number")
    fun getScansByDocumentId(documentId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE document_id = :documentId ORDER BY page_number")
    suspend fun getScansByDocumentIdSync(documentId: Long): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getScanById(id: Long): ScanEntity?

    // Counters / utilities

    @Query("SELECT COUNT(*) FROM scans WHERE document_id = :documentId")
    suspend fun getScanCountByDocument(documentId: Long): Int

    @Query("SELECT IFNULL(MAX(page_number), 0) FROM scans WHERE document_id = :documentId")
    suspend fun getMaxPageNumber(documentId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM scans WHERE checksum = :checksum)")
    suspend fun existsByChecksum(checksum: String): Boolean

    // Writes

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScans(scans: List<ScanEntity>): List<Long>

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Delete
    suspend fun deleteScan(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE document_id = :documentId")
    suspend fun deleteScansByDocumentId(documentId: Long)

    // Reordering (optional)

    @Transaction
    suspend fun resequencePages(documentId: Long) {
        val scans = getScansByDocumentIdSync(documentId).sortedBy { it.pageNumber }
        var idx = 1
        for (s in scans) {
            if (s.pageNumber != idx) {
                updateScan(s.copy(pageNumber = idx))
            }
            idx++
        }
    }
}