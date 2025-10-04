package com.jascanner.data.database

import android.content.Context
import androidx.room.*
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanDao
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.entities.ScanEntity

@Database(
    entities = [DocumentEntity::class, ScanEntity::class],
    version = 1,
    exportSchema = true
)
abstract class JAScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun scanDao(): ScanDao

    companion object {
        private const val DATABASE_NAME = "jascanner_db"

        @Volatile
        private var INSTANCE: JAScannerDatabase? = null

        fun getInstance(context: Context): JAScannerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JAScannerDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // For development; use proper migrations in production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
