package com.jascanner.export

enum class ExportFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

enum class QualityProfile(val displayName: String, val jpegQuality: Int, val webpQuality: Int) {
    MAXIMUM("Maximum", 100, 100),
    HIGH("High", 95, 95),
    BALANCED("Balanced", 85, 80),
    MOBILE("Mobile", 75, 70),
    ULTRA("Ultra", 60, 50)
}