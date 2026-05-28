package com.filebridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY id ASC")
    fun getAllFiles(): Flow<List<UploadedFile>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Int): UploadedFile?

    @Insert
    suspend fun insertFile(file: UploadedFile): Long

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFile(id: Int)

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getFileCount(): Int
}
