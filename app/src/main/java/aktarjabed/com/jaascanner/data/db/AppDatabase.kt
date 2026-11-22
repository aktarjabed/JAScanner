package aktarjabed.com.jaascanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import aktarjabed.com.jaascanner.core.TextSearch

@Database(entities = [DocumentEntity::class, FolderEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    abstract fun folderDao(): FolderDao

    companion object {
        /** Version 1 had flat folders and no pins. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders (parentId)")
            }
        }

        /**
         * Version 3 adds a normalised copy of the OCR text for Unicode-aware library search.
         * Existing rows are rewritten here rather than left with an ASCII-only lower-case copy,
         * so documents indexed before the upgrade are searchable the same way as new ones.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
                db.query("SELECT id, ocrText FROM documents").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val textIndex = cursor.getColumnIndexOrThrow("ocrText")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val text = cursor.getString(textIndex).orEmpty()
                        db.execSQL(
                            "UPDATE documents SET searchText = ? WHERE id = ?",
                            arrayOf<Any>(TextSearch.normalize(text), id),
                        )
                    }
                }
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jascanner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
