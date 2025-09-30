package com.jascanner.compression.domain.model

import android.graphics.Bitmap

enum class OutputFormat(
    val displayName: String,
    val extension: String,
    val supportsMultiPage: Boolean,
    val supportsTransparency: Boolean
) {
    JPG("JPEG", "jpg", false, false),
    PNG("PNG", "png", false, true),
    WEBP("WebP", "webp", false, true),
    PDF("PDF Document", "pdf", true, false)
}

enum class CompressionProfile(
    val displayName: String,
    val description: String,
    val maxDpi: Int,
    val jpegQuality: Int,
    val webpQuality: Int,
    val applyBinarization: Boolean,
    val targetFileSize: FileSize,
    val estimatedSizePerPage: String
) {
    MAXIMUM_QUALITY(
        displayName = "Maximum Quality",
        description = "Archival-grade, lossless compression",
        maxDpi = 300,
        jpegQuality = 100,
        webpQuality = 100,
        applyBinarization = false,
        targetFileSize = FileSize.VERY_LARGE,
        estimatedSizePerPage = "2-5 MB"
    ),
    HIGH_QUALITY(
        displayName = "High Quality",
        description = "Near-lossless with minimal compression",
        maxDpi = 300,
        jpegQuality = 95,
        webpQuality = 95,
        applyBinarization = false,
        targetFileSize = FileSize.LARGE,
        estimatedSizePerPage = "1-3 MB"
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Optimal quality-to-size ratio",
        maxDpi = 200,
        jpegQuality = 75,
        webpQuality = 80,
        applyBinarization = true,
        targetFileSize = FileSize.MEDIUM,
        estimatedSizePerPage = "300-800 KB"
    ),
    MOBILE_OPTIMIZED(
        displayName = "Mobile Optimized",
        description = "Small file size for sharing",
        maxDpi = 150,
        jpegQuality = 60,
        webpQuality = 70,
        applyBinarization = true,
        targetFileSize = FileSize.SMALL,
        estimatedSizePerPage = "100-300 KB"
    ),
    ULTRA_COMPRESSED(
        displayName = "Ultra Compressed",
        description = "Minimum file size, may reduce quality",
        maxDpi = 100,
        jpegQuality = 40,
        webpQuality = 50,
        applyBinarization = true,
        targetFileSize = FileSize.VERY_SMALL,
        estimatedSizePerPage = "50-150 KB"
    )
}

enum class FileSize {
    VERY_SMALL, SMALL, MEDIUM, LARGE, VERY_LARGE
}

data class CompressionSettings(
    val profile: CompressionProfile,
    val outputFormat: OutputFormat,
    val enableAdaptiveBinarization: Boolean = true,
    val enableDenoise: Boolean = true,
    val enableDeskew: Boolean = true,
    val enablePdfLinearization: Boolean = true,
    val maintainPdfACompliance: Boolean = true,
    val colorMode: ColorMode = ColorMode.AUTO,
    val customDpi: Int? = null,
    val customQuality: Int? = null
)

enum class ColorMode {
    AUTO,          // Detect if document is B&W or color
    GRAYSCALE,     // Force grayscale
    MONOCHROME,    // Force black & white (1-bit)
    COLOR          // Keep original colors
}

data class CompressionResult(
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val compressionRatio: Float,
    val processingTimeMs: Long,
    val appliedTechniques: List<String>,
    val outputFormat: OutputFormat,
    val pageCount: Int,
    val averageSizePerPage: Long
)

data class FormatSpecificSettings(
    // JPEG settings
    val jpegProgressive: Boolean = false,
    val jpegOptimize: Boolean = true,
    val jpegChromaSubsampling: ChromaSubsampling = ChromaSubsampling.YUV_420,

    // PNG settings
    val pngInterlaced: Boolean = false,
    val pngBitDepth: PngBitDepth = PngBitDepth.DEPTH_8,

    // WebP settings
    val webpLossless: Boolean = false,
    val webpMethod: Int = 4, // 0-6, higher = slower but smaller

    // PDF settings
    val pdfLinearize: Boolean = true,
    val pdfCompressStreams: Boolean = true,
    val pdfRemoveMetadata: Boolean = false,
    val pdfOptimizeImages: Boolean = true
)

enum class ChromaSubsampling {
    YUV_444,  // No subsampling (best quality)
    YUV_422,  // 2:1 horizontal subsampling
    YUV_420   // 2:1 in both directions (smallest)
}

enum class PngBitDepth {
    DEPTH_1,   // Monochrome
    DEPTH_8,   // 256 colors
    DEPTH_24,  // True color
    DEPTH_32   // True color + alpha
}