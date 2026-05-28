package com.filebridge.data.repository

import android.net.Uri
import android.util.Log
import com.filebridge.data.db.DeletedFile
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
    val deletedFiles: Flow<List<DeletedFile>> = fileDao.getDeletedFiles()

    suspend fun uploadFile(uri: Uri): Result<UploadedFile> = runCatching {
        val id = fileDao.getMaxId() + 1
        val stored = storageManager.copyFileToStorage(uri, id).getOrThrow()

        val file = UploadedFile(
            id = 0,
            fileName = stored.fileName,
            filePath = stored.filePath,
            fileSize = stored.fileSize,
            mimeType = stored.mimeType,
            fileHash = stored.fileHash
        )
        val insertedId = fileDao.insertFile(file)
        file.copy(id = insertedId.toInt())
    }

    suspend fun getFileById(id: Int): UploadedFile? = fileDao.getFileById(id)

    suspend fun deleteFile(id: Int): Boolean {
        val file = fileDao.getFileById(id) ?: return false
        val trashPath = storageManager.moveToTrash(file.filePath, id)
        if (trashPath == null) {
            Log.e(TAG, "Failed to move file to trash, aborting delete")
            return false
        }
        val deletedFile = DeletedFile(
            originalId = file.id,
            fileName = file.fileName,
            filePath = trashPath,
            originalPath = file.filePath,
            fileSize = file.fileSize,
            mimeType = file.mimeType,
            fileHash = file.fileHash,
            createdAt = file.createdAt,
            deletedAt = System.currentTimeMillis()
        )
        fileDao.insertDeletedFile(deletedFile)
        fileDao.deleteFile(id)
        return true
    }

    suspend fun restoreFile(id: Int): Boolean {
        val deleted = fileDao.getDeletedFileById(id) ?: return false
        val success = storageManager.restoreFromTrash(deleted.filePath, deleted.originalPath)
        if (!success) {
            Log.e(TAG, "Failed to restore file from trash")
            return false
        }
        val restored = UploadedFile(
            id = deleted.originalId,
            fileName = deleted.fileName,
            filePath = deleted.originalPath,
            fileSize = deleted.fileSize,
            mimeType = deleted.mimeType,
            fileHash = deleted.fileHash,
            createdAt = deleted.createdAt
        )
        fileDao.insertFile(restored)
        fileDao.permanentlyDeleteFile(id)
        return true
    }

    suspend fun permanentlyDeleteFile(id: Int): Boolean {
        val deleted = fileDao.getDeletedFileById(id) ?: return false
        storageManager.permanentlyDelete(deleted.filePath)
        fileDao.permanentlyDeleteFile(id)
        return true
    }

    suspend fun emptyTrash() {
        val files = fileDao.getDeletedFilesOnce()
        files.forEach { storageManager.permanentlyDelete(it.filePath) }
        fileDao.emptyTrash()
    }

    suspend fun getFileCount(): Int = fileDao.getFileCount()

    suspend fun importExistingFiles(): Int {
        val dir = File(FileStorageManager.DEFAULT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.i(TAG, "Created storage directory: ${dir.absolutePath}")
        }

        val trashDir = File(FileStorageManager.TRASH_DIR)
        if (!trashDir.exists()) trashDir.mkdirs()

        val existingFiles = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val dbFiles = fileDao.getAllFilesOnce()
        val dbPaths = dbFiles.map { it.filePath }.toSet()

        var nextId = fileDao.getMaxId() + 1
        var imported = 0
        for (file in existingFiles) {
            if (file.absolutePath !in dbPaths) {
                try {
                    val name = file.name
                    val mimeType = getMimeType(name)
                    val hash = computeFileHash(file)
                    val uploadedFile = UploadedFile(
                        id = nextId,
                        fileName = name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        mimeType = mimeType,
                        fileHash = hash
                    )
                    fileDao.insertFile(uploadedFile)
                    nextId++
                    imported++
                    Log.i(TAG, "Imported: $name (id=$nextId)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import: ${file.name}", e)
                }
            }
        }

        if (imported > 0) {
            Log.i(TAG, "Imported $imported existing files")
        }

        // 扫描回收站目录
        importTrashFiles()

        return imported
    }

    private suspend fun importTrashFiles() {
        val trashDir = File(FileStorageManager.TRASH_DIR)
        if (!trashDir.exists()) return

        val trashFiles = trashDir.listFiles()?.filter { it.isFile } ?: return
        val dbDeleted = fileDao.getDeletedFilesOnce()
        val dbDeletedPaths = dbDeleted.map { it.filePath }.toSet()

        var imported = 0
        for (file in trashFiles) {
            if (file.absolutePath in dbDeletedPaths) continue

            try {
                val fullName = file.name
                // 解析 originalId: 文件名格式为 {id}_{originalFileName}
                // id 是纯数字，originalName 可能包含 _
                var idEnd = 0
                while (idEnd < fullName.length && fullName[idEnd].isDigit()) {
                    idEnd++
                }
                if (idEnd == 0 || idEnd >= fullName.length || fullName[idEnd] != '_') continue

                val originalId = fullName.substring(0, idEnd).toIntOrNull() ?: continue
                val originalName = fullName.substring(idEnd + 1)
                val originalPath = "${FileStorageManager.DEFAULT_DIR}/${originalName}"
                val mimeType = getMimeType(originalName)
                val hash = computeFileHash(file)

                // 检查是否已存在同 ID 的记录
                val existingDeleted = fileDao.getDeletedFileById(originalId)
                if (existingDeleted != null) continue

                val deletedFile = DeletedFile(
                    originalId = originalId,
                    fileName = originalName,
                    filePath = file.absolutePath,
                    originalPath = originalPath,
                    fileSize = file.length(),
                    mimeType = mimeType,
                    fileHash = hash,
                    createdAt = file.lastModified(),
                    deletedAt = file.lastModified()
                )
                fileDao.insertDeletedFile(deletedFile)
                imported++
                Log.i(TAG, "Imported trash file: $fullName (originalId=$originalId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import trash file: ${file.name}", e)
            }
        }

        if (imported > 0) {
            Log.i(TAG, "Imported $imported trash files")
        }
    }

    suspend fun getDuplicateHashes(): Map<String, List<Int>> {
        val files = fileDao.getAllFilesOnce()
        return files.groupBy { it.fileHash }
            .filter { it.key.isNotEmpty() && it.value.size > 1 }
            .mapValues { entry -> entry.value.map { it.id } }
    }

    suspend fun getDeletedDuplicateHashes(): Map<String, List<Int>> {
        val deleted = fileDao.getDeletedFilesOnce()
        val activeFiles = fileDao.getAllFilesOnce()
        val activeHashMap = activeFiles.groupBy { it.fileHash }
            .filter { it.key.isNotEmpty() }
            .mapValues { entry -> entry.value.map { it.id } }
        val result = mutableMapOf<String, List<Int>>()
        for (deletedFile in deleted) {
            if (deletedFile.fileHash.isNotEmpty() && deletedFile.fileHash in activeHashMap) {
                result[deletedFile.fileHash] = activeHashMap[deletedFile.fileHash]!!
            }
        }
        return result
    }

    suspend fun getTrashInternalDuplicateHashes(): Map<String, List<Int>> {
        val deleted = fileDao.getDeletedFilesOnce()
        return deleted.groupBy { it.fileHash }
            .filter { it.key.isNotEmpty() && it.value.size > 1 }
            .mapValues { entry -> entry.value.map { it.originalId } }
    }

    private fun computeFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute file hash", e)
            ""
        }
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
