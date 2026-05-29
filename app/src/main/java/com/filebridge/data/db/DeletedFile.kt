package com.filebridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_files")
data class DeletedFile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val originalId: Int,
    val fileName: String,
    val filePath: String,
    val originalPath: String,
    val fileSize: Long,
    val mimeType: String,
    val fileHash: String = "",
    val createdAt: Long,
    val deletedAt: Long
)
