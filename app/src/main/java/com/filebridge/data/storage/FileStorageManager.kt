package com.filebridge.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_DIR = "/sdcard/OpenCodeFiles"
    }

    private val storageDir: File
        get() {
            val dir = File(DEFAULT_DIR)
            if (!dir.exists()) dir.mkdirs()
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
        val file = File(filePath)
        return if (file.exists()) file.delete() else false
    }

    fun getStorageDirPath(): String = storageDir.absolutePath

    private fun getFileName(uri: Uri): String {
        var name = "unknown_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
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
