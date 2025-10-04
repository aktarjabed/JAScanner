package com.jascanner.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    var pageCount: Int,
    var fullOcrText: String = "",
    val createdAt: Long,
    var updatedAt: Long
)