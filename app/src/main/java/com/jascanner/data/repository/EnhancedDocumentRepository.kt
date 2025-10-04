package com.jascanner.data.repository

import android.graphics.BitmapFactory
import com.jascanner.ai.ocr.SmartOcrEngine
import com.jascanner.ai.preprocess.AiPreprocessor
import com.jascanner.core.EnhancedErrorHandler
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanDao
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.entities.ScanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val scanDao: ScanDao,
    private val aiPreprocessor: AiPreprocessor,
    private val smartOcrEngine: SmartOcrEngine,
    private val errorHandler: EnhancedErrorHandler
) {

    // Document operations
    fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocumentsFlow()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> = documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): EnhancedErrorHandler.Result<DocumentEntity?> =
        errorHandler.safeExecuteAsync("Get document by ID") {
            documentDao.getDocumentById(id)
        }

    suspend fun createDocument(title: String): EnhancedErrorHandler.Result<Long> =
        errorHandler.safeExecuteAsync("Create document") {
            val document = DocumentEntity(
                title = title,
                pageCount = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            documentDao.insertDocument(document)
        }

    suspend fun updateDocument(document: DocumentEntity): EnhancedErrorHandler.Result<Unit> =
        errorHandler.safeExecuteAsync("Update document") {
            documentDao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))
        }

    suspend fun deleteDocument(documentId: Long): EnhancedErrorHandler.Result<Unit> =
        errorHandler.safeExecuteAsync("Delete document") {
            // Get scans to clean up files
            val scans = scanDao.getScansByDocumentIdSync(documentId)
            scans.forEach { scan ->
                try {
                    File(scan.imagePath).delete()
                } catch (e: Exception) {
                    Timber.w("Failed to delete scan image: ${scan.imagePath}")
                }
            }
            documentDao.deleteDocumentById(documentId)
        }

    // Scan operations
    fun getScansForDocument(documentId: Long): Flow<List<ScanEntity>> =
        scanDao.getScansByDocumentId(documentId)

    suspend fun addScanToDocument(
        documentId: Long,
        imagePath: String
    ): EnhancedErrorHandler.Result<Long> =
        errorHandler.safeExecuteAsync("Add scan to document") {
            withContext(Dispatchers.IO) {
                val document = documentDao.getDocumentById(documentId)
                    ?: throw IllegalArgumentException("Document not found: $documentId")

                val nextPageNumber = scanDao.getMaxPageNumber(documentId) + 1
                val checksum = calculateFileChecksum(imagePath)

                // Check for duplicate
                if (checksum != null && scanDao.existsByChecksum(checksum)) {
                    Timber.w("Duplicate scan detected, skipping: $checksum")
                    return@withContext -1L
                }

                var ocrText = ""
                var confidence = 0f

                // Run AI preprocessing and OCR
                val imageFile = File(imagePath)
                if (imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)

                    // Preprocess image
                    aiPreprocessor.process(bitmap).onSuccess { result ->
                        // Run OCR on preprocessed image
                        smartOcrEngine.recognize(result.processedBitmap).onSuccess { ocr ->
                            ocrText = ocr.data.text
                            confidence = ocr.data.confidence
                            Timber.d("OCR completed: confidence=$confidence, text length=${ocrText.length}")
                        }
                    }
                }


                // Create scan entity
                val scan = ScanEntity(
                    documentId = documentId,
                    pageNumber = nextPageNumber,
                    imagePath = imagePath,
                    ocrText = ocrText,
                    confidence = confidence,
                    checksum = checksum,
                    createdAt = System.currentTimeMillis()
                )

                val scanId = scanDao.insertScan(scan)

                // Update document with aggregated OCR text and page count
                val allScans = scanDao.getScansByDocumentIdSync(documentId)
                val fullOcrText = allScans.joinToString("\n\n") { it.ocrText }

                val updatedDocument = document.copy(
                    pageCount = allScans.size,
                    fullOcrText = fullOcrText,
                    updatedAt = System.currentTimeMillis()
                )

                documentDao.updateDocument(updatedDocument)

                Timber.d("Scan added successfully: scanId=$scanId, pages=${allScans.size}")
                scanId
            }
        }

    // AI-enhanced operations
    suspend fun reprocessDocumentWithAI(documentId: Long): EnhancedErrorHandler.Result<Unit> =
        errorHandler.safeExecuteAsync("Reprocess document with AI") {
            withContext(Dispatchers.IO) {
                val scans = scanDao.getScansByDocumentIdSync(documentId)
                val updatedScans = mutableListOf<ScanEntity>()

                for (scan in scans) {
                    val imageFile = File(scan.imagePath)
                    if (imageFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(scan.imagePath)

                        // Enhanced preprocessing
                        aiPreprocessor.process(bitmap).onSuccess { result ->
                            // Enhanced OCR
                            smartOcrEngine.recognize(result.processedBitmap).onSuccess { ocr ->
                                val updatedScan = scan.copy(
                                    ocrText = ocr.data.text,
                                    confidence = ocr.data.confidence
                                )
                                updatedScans.add(updatedScan)
                                Timber.d("Reprocessed scan ${scan.id}: confidence improved from ${scan.confidence} to ${ocr.data.confidence}")
                            }
                        }
                    }
                }

                // Update all scans
                if (updatedScans.isNotEmpty()) {
                    scanDao.updateScans(updatedScans)

                    // Update document
                    val document = documentDao.getDocumentById(documentId)
                    document?.let { doc ->
                        val fullOcrText = updatedScans.joinToString("\n\n") { it.ocrText }
                        documentDao.updateDocument(
                            doc.copy(
                                fullOcrText = fullOcrText,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                Timber.d("Document reprocessing completed: $documentId, ${updatedScans.size} scans updated")
            }
        }

    suspend fun getDocumentAnalytics(documentId: Long): EnhancedErrorHandler.Result<DocumentAnalytics?> =
        errorHandler.safeExecuteAsync("Get document analytics") {
            val document = documentDao.getDocumentById(documentId)
                ?: return@safeExecuteAsync null

            val scans = scanDao.getScansByDocumentIdSync(documentId)
            val avgConfidence = if (scans.isEmpty()) 0f else scans.map { it.confidence }.average().toFloat()
            val totalWords = scans.sumOf { it.ocrText.split("\\s+".toRegex()).size }
            val lowQualityScans = scans.count { it.confidence < 0.7f }

            DocumentAnalytics(
                documentId = documentId,
                title = document.title,
                pageCount = scans.size,
                averageConfidence = avgConfidence,
                totalWords = totalWords,
                lowQualityPages = lowQualityScans,
                createdAt = document.createdAt,
                lastProcessed = document.updatedAt
            )
        }

    fun searchInDocumentContent(query: String): Flow<List<ScanEntity>> =
        scanDao.searchByOcrText(query)

    private fun calculateFileChecksum(filePath: String): String? {
        return try {
            val file = File(filePath)
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            file.inputStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate checksum for: $filePath")
            null
        }
    }
}

data class DocumentAnalytics(
    val documentId: Long,
    val title: String,
    val pageCount: Int,
    val averageConfidence: Float,
    val totalWords: Int,
    val lowQualityPages: Int,
    val createdAt: Long,
    val lastProcessed: Long
)