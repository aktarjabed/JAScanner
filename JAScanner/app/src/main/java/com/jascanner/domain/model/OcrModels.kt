package com.jascanner.domain.model

import android.graphics.RectF

data class OcrTextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val boundingBox: RectF,
    val confidence: Float,
    val language: String = "en",
    val fontSize: Float = 12f,
    val isEdited: Boolean = false,
    val editedText: String? = null,
    val lineNumber: Int = 0,
    val wordCount: Int = text.split("\\s+".toRegex()).size
)

data class OcrResult(
    val textBlocks: List<OcrTextBlock>,
    val fullText: String,
    val confidence: Float,
    val language: String,
    val patterns: ExtractedPatterns? = null
)

data class ExtractedPatterns(
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val amounts: List<Money> = emptyList(),
    val addresses: List<Address> = emptyList()
)

data class Money(
    val amount: Double,
    val currency: String,
    val formatted: String
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
)