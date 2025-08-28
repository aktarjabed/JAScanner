package com.jascanner.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val documentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

