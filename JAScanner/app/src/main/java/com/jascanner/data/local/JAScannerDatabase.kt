package com.jascanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jascanner.data.local.converters.RoomConverters
import com.jascanner.data.local.entities.DocumentEntity
import com.jascanner.data.local.entities.PageEntity

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // Add auto migrations here if needed
    ]
)
@TypeConverters(RoomConverters::class)
abstract class JAScannerDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: JAScannerDatabase? = null

        // Migration from version 1 to 2 (example)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns or tables
                database.execSQL(
                    "ALTER TABLE documents ADD COLUMN tags TEXT"
                )
            }
        }

        fun getInstance(context: Context): JAScannerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JAScannerDatabase::class.java,
                    "jascanner_database"
                )
                    .addMigrations(MIGRATION_1_2) // Add migrations here
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}