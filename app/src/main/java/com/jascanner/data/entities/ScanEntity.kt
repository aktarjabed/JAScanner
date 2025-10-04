package com.jascanner.data.entities

import androidx.room.*

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
        Index(value = ["page_number"])
    ]
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "ocr_text") val ocrText: String = "",
    @ColumnInfo(name = "confidence") val confidence: Float = 0f,
    @ColumnInfo(name = "checksum") val checksum: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)