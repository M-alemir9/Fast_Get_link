package com.fastget.downloader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fastget.downloader.data.model.DownloadStatus
import com.fastget.downloader.data.model.FileCategory

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val destinationPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val category: FileCategory,
    val mimeType: String,
    val supportsRange: Boolean,
    val chunkCount: Int,
    val speedLimitBytesPerSec: Long,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val checksumSha256: String? = null
)

@Entity(
    tableName = "download_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId", "chunkIndex"], unique = true)]
)
data class DownloadChunkEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val totalChunkBytes: Long,
    val tempFilePath: String,
    val isCompleted: Boolean = false
)
