package com.jascanner.data.repository

import android.content.Context
import android.graphics.RectF
import com.jascanner.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatchOperationsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val editorRepository: EditorRepository
) {

    private val mutex = Mutex()

    suspend fun executeBatchOperation(
        documentIds: List<String>,
        operation: BatchOperation,
        configuration: BatchOperationConfig,
        progressCallback: (BatchProgress) -> Unit
    ): BatchOperationResult = withContext(Dispatchers.IO) {

        // Use atomic counters to prevent race conditions
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val errors = mutableListOf<BatchOperationError>()
        val errorsMutex = Mutex()

        try {
            documentIds.forEachIndexed { index, documentId ->
                try {
                    progressCallback(
                        BatchProgress(
                            current = index + 1,
                            total = documentIds.size,
                            message = "Processing document ${index + 1} of ${documentIds.size}",
                            documentId = documentId
                        )
                    )

                    // Load document with timeout
                    val document = withTimeout(30_000) {
                        documentRepository.loadEditableDocument(documentId)
                    } ?: throw IllegalStateException("Document not found")

                    // Process document
                    val processed = processDocumentSafely(document, operation, configuration)

                    // Save with mutex to prevent concurrent writes
                    mutex.withLock {
                        documentRepository.saveEditableDocument(processed)
                    }

                    successCount.incrementAndGet()

                } catch (e: Exception) {
                    Timber.e(e, "Error processing document: $documentId")

                    // Thread-safe error collection
                    errorsMutex.withLock {
                        errors.add(
                            BatchOperationError(
                                documentId = documentId,
                                message = e.message ?: "Unknown error",
                                exception = e
                            )
                        )
                    }

                    failureCount.incrementAndGet()
                }
            }

            // Return result based on counts
            when {
                failureCount.get() == 0 -> {
                    BatchOperationResult.Success(
                        successCount = successCount.get(),
                        failureCount = 0
                    )
                }
                successCount.get() > 0 -> {
                    BatchOperationResult.PartialSuccess(
                        successCount = successCount.get(),
                        failureCount = failureCount.get(),
                        errors = errors
                    )
                }
                else -> {
                    BatchOperationResult.Error(
                        message = "All operations failed",
                        exception = Exception("Batch operation completely failed")
                    )
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Critical error in batch operation")
            BatchOperationResult.Error(
                message = "Critical error: ${e.message}",
                exception = e
            )
        }
    }

    private suspend fun processDocumentSafely(
        document: EditableDocument,
        operation: BatchOperation,
        configuration: BatchOperationConfig
    ): EditableDocument {
        return try {
            when (operation) {
                BatchOperation.APPLY_FILTER -> applyFilterToDocument(document, configuration)
                BatchOperation.ROTATE -> rotateDocument(document, configuration)
                BatchOperation.COMPRESS -> compressDocument(document, configuration)
                BatchOperation.WATERMARK -> addWatermarkToDocument(document, configuration)
                BatchOperation.MERGE -> document // Handled separately
                BatchOperation.CONVERT_TO_PDF -> {
                    convertDocumentToPdf(document, configuration)
                    document
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to process document: ${document.id}")
            throw e
        }
    }

    // Add timeout extension
    private suspend fun <T> withTimeout(
        timeMillis: Long,
        block: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeout(timeMillis) {
            block()
        }
    }

    // Basic functional implementations for batch operations
    private fun applyFilterToDocument(document: EditableDocument, config: BatchOperationConfig): EditableDocument {
        Timber.d("Applying filter to document ${document.id}")
        val newPages = document.pages.map { page ->
            page.copy(
                imageAdjustments = (page.imageAdjustments ?: ImageAdjustments(null, 0f, 1f, 1f, 0f)).copy(
                    filter = ImageFilter.BLACK_AND_WHITE
                ),
                modifiedAt = System.currentTimeMillis()
            )
        }
        return document.copy(pages = newPages, modifiedAt = System.currentTimeMillis())
    }

    private fun rotateDocument(document: EditableDocument, config: BatchOperationConfig): EditableDocument {
        Timber.d("Rotating document ${document.id}")
        val newPages = document.pages.map { page ->
            page.copy(
                rotation = (page.rotation + 90f) % 360f,
                modifiedAt = System.currentTimeMillis()
            )
        }
        return document.copy(pages = newPages, modifiedAt = System.currentTimeMillis())
    }

    private fun compressDocument(document: EditableDocument, config: BatchOperationConfig): EditableDocument {
        // This would typically affect saving, not the model itself.
        // For now, we'll just mark it as modified.
        Timber.d("Compressing document ${document.id}")
        return document.copy(modifiedAt = System.currentTimeMillis())
    }

    private fun addWatermarkToDocument(document: EditableDocument, config: BatchOperationConfig): EditableDocument {
        Timber.d("Adding watermark to document ${document.id}")
        val newPages = document.pages.map { page ->
            val watermark = Annotation.TextAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                pageId = page.pageId,
                timestamp = System.currentTimeMillis(),
                text = "CONFIDENTIAL",
                boundingBox = RectF(0.1f, 0.1f, 0.5f, 0.2f),
                fontSize = 24f,
                color = android.graphics.Color.RED
            )
            page.copy(
                annotations = page.annotations + watermark,
                modifiedAt = System.currentTimeMillis()
            )
        }
        return document.copy(pages = newPages, modifiedAt = System.currentTimeMillis())
    }

    private fun convertDocumentToPdf(document: EditableDocument, config: BatchOperationConfig) {
        // This operation is expected to be handled by a different service or have side effects.
        // For now, we just log the action.
        Timber.d("Converting document ${document.id} to PDF")
    }
}