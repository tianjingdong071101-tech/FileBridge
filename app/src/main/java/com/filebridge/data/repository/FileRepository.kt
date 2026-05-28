package com.filebridge.data.repository

import android.net.Uri
import android.util.Log
import com.filebridge.data.db.FileDao
import com.filebridge.data.db.UploadedFile
import com.filebridge.data.storage.FileStorageManager
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val fileDao: FileDao,
    private val storageManager: FileStorageManager
) {
    companion object {
        private const val TAG = "FileRepository"
    }

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

    suspend fun importExistingFiles(): Int {
        val dir = File(FileStorageManager.DEFAULT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.i(TAG, "Created storage directory: ${dir.absolutePath}")
            return 0
        }

        val existingFiles = dir.listFiles() ?: return 0
        val dbFiles = fileDao.getAllFilesOnce()
        val dbPaths = dbFiles.map { it.filePath }.toSet()

        var imported = 0
        for (file in existingFiles) {
            if (file.isFile && file.absolutePath !in dbPaths) {
                try {
                    val name = file.name
                    val mimeType = getMimeType(name)
                    val uploadedFile = UploadedFile(
                        id = 0,
                        fileName = name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        mimeType = mimeType
                    )
                    fileDao.insertFile(uploadedFile)
                    imported++
                    Log.i(TAG, "Imported: $name")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import: ${file.name}", e)
                }
            }
        }

        if (imported > 0) {
            Log.i(TAG, "Imported $imported existing files")
        }
        return imported
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "txt", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "ts" -> "text/javascript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            else -> "application/octet-stream"
        }
    }
}
