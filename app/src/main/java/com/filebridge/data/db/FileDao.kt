package com.filebridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY id ASC")
    fun getAllFiles(): Flow<List<UploadedFile>>

    @Query("SELECT * FROM files ORDER BY id ASC")
    suspend fun getAllFilesOnce(): List<UploadedFile>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Int): UploadedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: UploadedFile): Long

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFile(id: Int)

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getFileCount(): Int

    // 回收站相关
    @Query("SELECT * FROM deleted_files ORDER BY deletedAt DESC")
    fun getDeletedFiles(): Flow<List<DeletedFile>>

    @Query("SELECT * FROM deleted_files ORDER BY deletedAt DESC")
    suspend fun getDeletedFilesOnce(): List<DeletedFile>

    @Query("SELECT * FROM deleted_files WHERE originalId = :id")
    suspend fun getDeletedFileById(id: Int): DeletedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedFile(file: DeletedFile)

    @Query("DELETE FROM deleted_files WHERE originalId = :id")
    suspend fun permanentlyDeleteFile(id: Int)

    @Query("DELETE FROM deleted_files")
    suspend fun emptyTrash()

    // 重复检测
    @Query("SELECT * FROM files WHERE fileHash = :hash AND fileHash != ''")
    suspend fun getFilesByHash(hash: String): List<UploadedFile>

    @Query("SELECT * FROM deleted_files WHERE fileHash = :hash AND fileHash != ''")
    suspend fun getDeletedFilesByHash(hash: String): List<DeletedFile>

    @Query("""
        SELECT COALESCE(MAX(id), 0) FROM (
            SELECT id FROM files
            UNION ALL
            SELECT originalId AS id FROM deleted_files
        )
    """)
    suspend fun getMaxId(): Int
}
