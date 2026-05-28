package com.filebridge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UploadedFile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
}
