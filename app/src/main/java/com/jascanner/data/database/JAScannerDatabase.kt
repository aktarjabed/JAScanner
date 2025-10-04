package com.jascanner.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanDao
import com.jascanner.data.entities.DocumentEntity
import com.jascanner.data.entities.ScanEntity
import timber.log.Timber

@Database(
    entities = [DocumentEntity::class, ScanEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class JAScannerDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun scanDao(): ScanDao

    companion object {
        private const val DATABASE_NAME = "jascanner_database"

        @Volatile
        private var INSTANCE: JAScannerDatabase? = null

        fun getInstance(context: Context): JAScannerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JAScannerDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Added for safety during development
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // This migration assumes the database starts at version 1 with no tables
        // and migrates to version 2 with the documents and scans tables.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Timber.d("Migrating database from version 1 to 2")

                // Create documents table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `fullOcrText` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create scans table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `scans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `document_id` INTEGER NOT NULL,
                        `page_number` INTEGER NOT NULL,
                        `image_path` TEXT NOT NULL,
                        `ocr_text` TEXT NOT NULL DEFAULT '',
                        `confidence` REAL NOT NULL DEFAULT 0.0,
                        `checksum` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`document_id`) REFERENCES `documents`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create indices for scans table
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_scans_document_id` ON `scans` (`document_id`)")

                Timber.d("Database migrated successfully to version 2")
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Timber.d("JAScanner database created successfully")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON")
                Timber.d("JAScanner database opened and foreign keys enabled")
            }
        }
    }
}

class DatabaseConverters {
    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        return if (value.isEmpty()) emptyList()
               else value.split(",").map { it.toLong() }
    }
}