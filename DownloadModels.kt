package com.fastget.downloader.data.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    MERGING
}

enum class FileCategory {
    VIDEO,
    AUDIO,
    ARCHIVE,
    DOCUMENT,
    APK,
    IMAGE,
    OTHER
}

data class MediaQualityOption(
    val id: String,
    val label: String,
    val format: String,
    val resolution: String? = null,
    val bitrate: String? = null,
    val fileSize: Long,
    val isAudioOnly: Boolean = false,
    val downloadUrl: String
)

data class HeadInspectionResult(
    val url: String,
    val fileName: String,
    val totalBytes: Long,
    val mimeType: String,
    val supportsRange: Boolean,
    val category: FileCategory,
    val mediaFormats: List<MediaQualityOption> = emptyList()
)
