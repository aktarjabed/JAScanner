package com.jascanner.utils

import android.graphics.Bitmap
import com.jascanner.domain.model.EditableDocument
import com.jascanner.domain.model.EditablePage
import timber.log.Timber
import java.io.File

object ValidationUtils {

    fun validateDocument(document: EditableDocument): ValidationResult {
        val errors = mutableListOf<String>()

        if (document.name.isBlank()) {
            errors.add("Document name cannot be blank")
        }

        if (document.pages.isEmpty()) {
            errors.add("Document must have at least one page")
        }

        document.pages.forEachIndexed { index, page ->
            val pageErrors = validatePage(page)
            if (!pageErrors.isValid) {
                errors.addAll(pageErrors.errors.map { "Page $index: $it" })
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(isValid = false, errors = errors)
        }
    }

    fun validatePage(page: EditablePage): ValidationResult {
        val errors = mutableListOf<String>()

        if (page.originalBitmap == null && page.processedBitmap == null) {
            errors.add("Page must have at least one bitmap")
        }

        if (page.width <= 0 || page.height <= 0) {
            errors.add("Page dimensions must be positive")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validateBitmap(bitmap: Bitmap?): ValidationResult {
        if (bitmap == null) {
            return ValidationResult(
                isValid = false,
                errors = listOf("Bitmap is null")
            )
        }

        val errors = mutableListOf<String>()

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            errors.add("Bitmap dimensions must be positive")
        }

        if (bitmap.isRecycled) {
            errors.add("Bitmap is recycled")
        }

        val pixelCount = bitmap.width * bitmap.height
        if (pixelCount > MAX_PIXELS) {
            errors.add("Bitmap exceeds maximum pixel count")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validateFileName(fileName: String): ValidationResult {
        val errors = mutableListOf<String>()

        if (fileName.isBlank()) {
            errors.add("Filename cannot be blank")
        }

        if (fileName.length > MAX_FILENAME_LENGTH) {
            errors.add("Filename too long (max $MAX_FILENAME_LENGTH characters)")
        }

        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (fileName.any { it in invalidChars }) {
            errors.add("Filename contains invalid characters")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validatePdfFile(file: File): ValidationResult {
        val errors = mutableListOf<String>()

        if (!file.exists()) {
            errors.add("File does not exist")
        }

        if (!file.canRead()) {
            errors.add("File is not readable")
        }

        if (!file.name.endsWith(".pdf", ignoreCase = true)) {
            errors.add("File is not a PDF")
        }

        if (file.length() > MAX_PDF_SIZE) {
            errors.add("PDF file too large (max ${MAX_PDF_SIZE / (1024 * 1024)} MB)")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun validatePageNumber(pageNumber: Int, totalPages: Int): ValidationResult {
        return if (pageNumber in 1..totalPages) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Page number $pageNumber is out of range (1-$totalPages)")
            )
        }
    }

    fun validateCompressionQuality(quality: Int): ValidationResult {
        return if (quality in 0..100) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Quality must be between 0 and 100")
            )
        }
    }

    fun validateRotationAngle(angle: Float): ValidationResult {
        val validAngles = listOf(0f, 90f, 180f, 270f, -90f, -180f, -270f)
        return if (angle in validAngles || angle % 90f == 0f) {
            ValidationResult(isValid = true)
        } else {
            ValidationResult(
                isValid = false,
                errors = listOf("Rotation angle must be a multiple of 90 degrees")
            )
        }
    }

    private const val MAX_PIXELS = 100 * 1024 * 1024 // 100 megapixels
    private const val MAX_FILENAME_LENGTH = 255
    private const val MAX_PDF_SIZE = 100 * 1024 * 1024L // 100 MB
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)