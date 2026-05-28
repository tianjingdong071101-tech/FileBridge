package com.filebridge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadedFile::class, DeletedFile::class],
    version = 2,
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
    }
}
