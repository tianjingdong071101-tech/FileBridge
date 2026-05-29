package com.filebridge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadedFile::class, DeletedFile::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN fileHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS deleted_files (
                        originalId INTEGER NOT NULL PRIMARY KEY,
                        fileName TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        originalPath TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        mimeType TEXT NOT NULL,
                        fileHash TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS deleted_files_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        originalId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        originalPath TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        mimeType TEXT NOT NULL,
                        fileHash TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO deleted_files_new (originalId, fileName, filePath, originalPath, fileSize, mimeType, fileHash, createdAt, deletedAt)
                    SELECT originalId, fileName, filePath, originalPath, fileSize, mimeType, fileHash, createdAt, deletedAt
                    FROM deleted_files
                """.trimIndent())
                db.execSQL("DROP TABLE deleted_files")
                db.execSQL("ALTER TABLE deleted_files_new RENAME TO deleted_files")
            }
        }
    }
}
