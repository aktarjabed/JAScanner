package com.jascanner.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageNumber: Int,
    val imagePath: String
)