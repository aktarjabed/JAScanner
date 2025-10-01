package com.jascanner.presentation.editor

enum class ExportFormat(val displayName: String, val extension: String) {
    PDF("PDF", ".pdf"),
    PDF_A("PDF/A", ".pdf"),
    IMAGES("Images", ".jpg"),
    TEXT("Text", ".txt")
}

data class ExportSettings(
    val format: ExportFormat,
    val includeAnnotations: Boolean = true,
    val includeOcrLayer: Boolean = true,
    val compressionQuality: Int = 85,
    val pdfLinearized: Boolean = false,
    val imageFormat: android.graphics.Bitmap.CompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
) {
    companion object {
        fun default() = ExportSettings(
            format = ExportFormat.PDF,
            includeAnnotations = true,
            includeOcrLayer = true,
            compressionQuality = 85,
            pdfLinearized = false
        )
    }
}