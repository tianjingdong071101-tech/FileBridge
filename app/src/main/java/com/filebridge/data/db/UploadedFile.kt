package com.filebridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class UploadedFile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis()
)
