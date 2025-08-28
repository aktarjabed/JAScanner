package com.jascanner.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanSessionDao
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.entities.ScanSessionEntity

@Database(entities = [DocumentEntity::class, ScanSessionEntity::class], version = 1, exportSchema = false)
abstract class JAScannerDatabase: RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun scanSessionDao(): ScanSessionDao
}

