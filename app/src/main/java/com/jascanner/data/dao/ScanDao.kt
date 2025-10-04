package com.jascanner.data.dao

import androidx.room.*
import com.jascanner.data.entities.OcrAnalytics
import com.jascanner.data.entities.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    // Flow-based readers for UI observation
    @Query("SELECT * FROM scans WHERE document_id = :documentId ORDER BY page_number ASC")
    fun getScansByDocumentId(documentId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY created_at DESC LIMIT :limit")
    fun getRecentScans(limit: Int = 50): Flow<List<ScanEntity>>

    // Synchronous readers for repository transactions
    @Query("SELECT * FROM scans WHERE document_id = :documentId ORDER BY page_number ASC")
    suspend fun getScansByDocumentIdSync(documentId: Long): List<ScanEntity>

    @Query("SELECT * FROM scans WHERE id = :scanId")
    suspend fun getScanById(scanId: Long): ScanEntity?

    @Query("SELECT * FROM scans WHERE checksum = :checksum LIMIT 1")
    suspend fun getScanByChecksum(checksum: String): ScanEntity?

    // Counters and utilities
    @Query("SELECT COUNT(*) FROM scans WHERE document_id = :documentId")
    suspend fun getScanCountByDocument(documentId: Long): Int

    @Query("SELECT COALESCE(MAX(page_number), 0) FROM scans WHERE document_id = :documentId")
    suspend fun getMaxPageNumber(documentId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM scans WHERE checksum = :checksum)")
    suspend fun existsByChecksum(checksum: String): Boolean

    @Query("SELECT COUNT(*) FROM scans WHERE confidence < :threshold")
    suspend fun getLowConfidenceCount(threshold: Float = 0.7f): Int

    // OCR and AI-related queries
    @Query("SELECT * FROM scans WHERE ocr_text LIKE '%' || :searchText || '%'")
    fun searchByOcrText(searchText: String): Flow<List<ScanEntity>>

    @Query("SELECT AVG(confidence) FROM scans WHERE document_id = :documentId")
    suspend fun getAverageConfidence(documentId: Long): Float

    @Query("SELECT * FROM scans WHERE confidence < :threshold ORDER BY confidence ASC")
    suspend fun getScansNeedingReprocessing(threshold: Float = 0.5f): List<ScanEntity>

    // CRUD Operations
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScans(scans: List<ScanEntity>): List<Long>

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Update
    suspend fun updateScans(scans: List<ScanEntity>)

    @Delete
    suspend fun deleteScan(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE document_id = :documentId")
    suspend fun deleteScansByDocumentId(documentId: Long)

    @Query("DELETE FROM scans WHERE id IN (:scanIds)")
    suspend fun deleteScansByIds(scanIds: List<Long>)

    // Batch operations for AI processing
    @Transaction
    suspend fun replaceScansForDocument(documentId: Long, newScans: List<ScanEntity>) {
        deleteScansByDocumentId(documentId)
        insertScans(newScans)
    }

    @Transaction
    suspend fun resequencePages(documentId: Long) {
        val scans = getScansByDocumentIdSync(documentId)
        scans.forEachIndexed { index, scan ->
            if (scan.pageNumber != index + 1) {
                updateScan(scan.copy(pageNumber = index + 1))
            }
        }
    }

    // Analytics for AI performance tracking
    @Query("""
        SELECT
            AVG(confidence) as avg_confidence,
            COUNT(*) as total_scans,
            COUNT(CASE WHEN confidence >= 0.8 THEN 1 END) as high_quality_scans,
            COUNT(CASE WHEN confidence < 0.5 THEN 1 END) as low_quality_scans
        FROM scans
        WHERE created_at >= :fromTimestamp
    """)
    suspend fun getOcrAnalytics(fromTimestamp: Long): OcrAnalytics

    @Query("SELECT * FROM scans WHERE LENGTH(ocr_text) = 0 AND confidence > 0")
    suspend fun getScansWithEmptyOcr(): List<ScanEntity>
}