package com.jascanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jascanner.data.local.converters.RoomConverters
import com.jascanner.data.local.entities.DocumentEntity
import com.jascanner.data.local.entities.PageEntity

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class JAScannerDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: JAScannerDatabase? = null

        fun getInstance(context: Context): JAScannerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JAScannerDatabase::class.java,
                    "jascanner_database"
                )
                    .fallbackToDestructiveMigration() // For development, can be removed for production
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}