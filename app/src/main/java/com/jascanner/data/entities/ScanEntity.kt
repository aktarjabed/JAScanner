package com.jascanner.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"]),
        Index(value = ["page_number"]),
        Index(value = ["confidence"]),
        Index(value = ["created_at"])
    ]
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "document_id")
    val documentId: Long,
    @ColumnInfo(name = "page_number")
    var pageNumber: Int,
    @ColumnInfo(name = "image_path")
    val imagePath: String,
    @ColumnInfo(name = "ocr_text", defaultValue = "")
    var ocrText: String,
    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    var confidence: Float,
    @ColumnInfo(name = "checksum")
    val checksum: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

data class OcrAnalytics(
    val avg_confidence: Float,
    val total_scans: Int,
    val high_quality_scans: Int,
    val low_quality_scans: Int
)