package com.jascanner.domain.model

enum class ExportFormat {
    PDF,
    PDF_A,
    IMAGES,
    TEXT
}

data class ExportSettings(
    val format: ExportFormat,
    val includeAnnotations: Boolean = true,
    val includeOcrLayer: Boolean = true,
    val compressionQuality: Int = 90,
    val pdfLinearized: Boolean = false,
    val imageFormat: android.graphics.Bitmap.CompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
) {
    companion object {
        fun default() = ExportSettings(
            format = ExportFormat.PDF,
            includeAnnotations = true,
            includeOcrLayer = true,
            compressionQuality = 90
        )
    }
}