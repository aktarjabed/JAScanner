package com.jascanner.domain.model

enum class BatchOperation {
    APPLY_FILTER,
    ROTATE,
    COMPRESS,
    WATERMARK,
    MERGE,
    CONVERT_TO_PDF
}

data class BatchOperationConfig(
    val continueOnError: Boolean = true
)

data class BatchProgress(
    val documentId: String,
    val current: Int,
    val total: Int,
    val message: String
)

sealed class BatchOperationResult {
    data class Success(val successCount: Int, val failureCount: Int) : BatchOperationResult()
    data class PartialSuccess(
        val successCount: Int,
        val failureCount: Int,
        val errors: List<BatchOperationError>
    ) : BatchOperationResult()
    data class Error(val message: String, val exception: Throwable) : BatchOperationResult()
}

data class BatchOperationError(
    val documentId: String,
    val message: String,
    val exception: Throwable
)