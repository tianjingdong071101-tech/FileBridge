package com.filebridge.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FileStorageManager"
        const val DEFAULT_DIR = "/sdcard/OpenCodeFiles"
        const val TRASH_DIR = "$DEFAULT_DIR/.trash"
    }

    private val storageDir: File
        get() {
            val dir = File(DEFAULT_DIR)
            val trashDir = File(TRASH_DIR)
            try {
                if (!dir.exists()) dir.mkdirs()
                if (!trashDir.exists()) trashDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create storage directory", e)
                val fallback = File(context.getExternalFilesDir(null), "OpenCodeFiles")
                val fallbackTrash = File(fallback, ".trash")
                if (!fallback.exists()) fallback.mkdirs()
                if (!fallbackTrash.exists()) fallbackTrash.mkdirs()
                return fallback
            }
            return dir
        }

    fun copyFileToStorage(sourceUri: Uri, fileId: Int): Result<StoredFile> = runCatching {
        val fileName = getFileName(sourceUri)
        val storedName = "${fileId}_${fileName}"
        val destFile = File(storageDir, storedName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for URI")

        val mimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
        val hash = computeFileHash(destFile)

        StoredFile(
            fileName = fileName,
            filePath = destFile.absolutePath,
            fileSize = destFile.length(),
            mimeType = mimeType,
            fileHash = hash
        )
    }

    fun moveToTrash(filePath: String, fileId: Int): String? {
        return try {
            val src = File(filePath)
            if (!src.exists()) return null
            val trashDir = File(TRASH_DIR)
            if (!trashDir.exists()) trashDir.mkdirs()
            var destName = "${fileId}_${src.name}"
            var dest = File(trashDir, destName)
            if (dest.exists()) {
                destName = "${fileId}_${System.currentTimeMillis()}_${src.name}"
                dest = File(trashDir, destName)
            }
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            src.delete()
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move to trash", e)
            null
        }
    }

    fun restoreFromTrash(trashPath: String, originalPath: String): Boolean {
        return try {
            val src = File(trashPath)
            if (!src.exists()) return false
            val dest = File(originalPath)
            dest.parentFile?.mkdirs()
            src.renameTo(dest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from trash", e)
            false
        }
    }

    fun permanentlyDelete(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to permanently delete", e)
            false
        }
    }

    fun computeHash(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute hash", e)
            ""
        }
    }

    private fun computeFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
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

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            false
        }
    }

    fun getStorageDirPath(): String = storageDir.absolutePath

    fun isStorageAvailable(): Boolean {
        return try {
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        } catch (e: Exception) {
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown_file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "unknown_file"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file name", e)
        }
        return name
    }
}

data class StoredFile(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val fileHash: String = ""
)
