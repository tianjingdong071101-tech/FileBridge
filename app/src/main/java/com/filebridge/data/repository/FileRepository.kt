package com.filebridge.data.repository

import android.net.Uri
import com.filebridge.data.db.FileDao
import com.filebridge.data.db.UploadedFile
import com.filebridge.data.storage.FileStorageManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val fileDao: FileDao,
    private val storageManager: FileStorageManager
) {
    val allFiles: Flow<List<UploadedFile>> = fileDao.getAllFiles()

    suspend fun uploadFile(uri: Uri): Result<UploadedFile> = runCatching {
        val id = fileDao.getFileCount() + 1
        val stored = storageManager.copyFileToStorage(uri, id).getOrThrow()

        val file = UploadedFile(
            id = 0,
            fileName = stored.fileName,
            filePath = stored.filePath,
            fileSize = stored.fileSize,
            mimeType = stored.mimeType
        )
        val insertedId = fileDao.insertFile(file)
        file.copy(id = insertedId.toInt())
    }

    suspend fun getFileById(id: Int): UploadedFile? = fileDao.getFileById(id)

    suspend fun deleteFile(id: Int): Boolean {
        val file = fileDao.getFileById(id) ?: return false
        storageManager.deleteFile(file.filePath)
        fileDao.deleteFile(id)
        return true
    }

    suspend fun getFileCount(): Int = fileDao.getFileCount()
}
