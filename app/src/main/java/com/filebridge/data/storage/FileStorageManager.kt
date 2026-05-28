package com.filebridge.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FileStorageManager"
        const val DEFAULT_DIR = "/sdcard/OpenCodeFiles"
    }

    private val storageDir: File
        get() {
            val dir = File(DEFAULT_DIR)
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create storage directory", e)
                // Fallback to app internal storage
                val fallback = File(context.getExternalFilesDir(null), "OpenCodeFiles")
                if (!fallback.exists()) fallback.mkdirs()
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

        StoredFile(
            fileName = fileName,
            filePath = destFile.absolutePath,
            fileSize = destFile.length(),
            mimeType = mimeType
        )
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
    val mimeType: String
)
