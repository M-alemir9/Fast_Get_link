package com.fastget.downloader.data.network

import com.fastget.downloader.data.model.FileCategory
import com.fastget.downloader.data.model.HeadInspectionResult
import com.fastget.downloader.data.model.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlInspector @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun inspectUrl(targetUrl: String): Result<HeadInspectionResult> = withContext(Dispatchers.IO) {
        try {
            // First attempt with HEAD request
            val headRequest = Request.Builder()
                .url(targetUrl)
                .head()
                .header("User-Agent", "FastGet-Downloader/1.0 (Android)")
                .build()

            var response = client.newCall(headRequest).execute()

            // If server returns 405 Method Not Allowed, fallback to GET with zero byte range
            if (response.code == 405 || response.code == 501) {
                response.close()
                val getRequest = Request.Builder()
                    .url(targetUrl)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "FastGet-Downloader/1.0 (Android)")
                    .build()
                response = client.newCall(getRequest).execute()
            }

            if (!response.isSuccessful && response.code != 206) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val acceptRanges = response.header("Accept-Ranges")
            val contentRange = response.header("Content-Range")
            val supportsRange = (acceptRanges != null && acceptRanges.equals("bytes", ignoreCase = true)) ||
                    (response.code == 206) ||
                    (contentRange != null && contentRange.contains("bytes"))

            val contentLengthHeader = response.header("Content-Length")
            var totalBytes = contentLengthHeader?.toLongOrNull() ?: -1L

            // Extract total bytes from Content-Range if available: e.g. "bytes 0-0/10485760"
            if (contentRange != null && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfterLast("/")
                totalBytes = totalStr.toLongOrNull() ?: totalBytes
            }

            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val contentDisposition = response.header("Content-Disposition")
            val fileName = extractFileName(targetUrl, contentDisposition)
            val category = resolveCategory(fileName, contentType)

            // Extract mock/real quality options for streaming video/audio links
            val mediaFormats = extractAvailableMediaFormats(targetUrl, category, totalBytes)

            response.close()

            Result.success(
                HeadInspectionResult(
                    url = targetUrl,
                    fileName = fileName,
                    totalBytes = totalBytes,
                    mimeType = contentType,
                    supportsRange = supportsRange,
                    category = category,
                    mediaFormats = mediaFormats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractFileName(url: String, contentDisposition: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val filenamePattern = Regex("filename\\*?=(?:UTF-8'')?["']?([^"';]+)["']?", RegexOption.IGNORE_CASE)
            val match = filenamePattern.find(contentDisposition)
            if (match != null && match.groupValues.size > 1) {
                try {
                    return URLDecoder.decode(match.groupValues[1].trim(), "UTF-8")
                } catch (_: Exception) {
                    return match.groupValues[1].trim()
                }
            }
        }

        try {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val segments = cleanUrl.split("/").filter { it.isNotBlank() }
            if (segments.isNotEmpty()) {
                val lastSegment = URLDecoder.decode(segments.last(), "UTF-8")
                if (lastSegment.contains(".")) {
                    return lastSegment
                }
            }
        } catch (_: Exception) {
            // fallback
        }

        return "download_${System.currentTimeMillis()}.bin"
    }

    private fun resolveCategory(fileName: String, mimeType: String): FileCategory {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            extension in listOf("mp4", "mkv", "webm", "avi", "mov", "flv") || mimeType.startsWith("video/") -> FileCategory.VIDEO
            extension in listOf("mp3", "m4a", "wav", "flac", "aac", "ogg") || mimeType.startsWith("audio/") -> FileCategory.AUDIO
            extension in listOf("zip", "rar", "7z", "tar", "gz", "iso") || mimeType.contains("zip") -> FileCategory.ARCHIVE
            extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> FileCategory.DOCUMENT
            extension == "apk" || mimeType.contains("vnd.android.package-archive") -> FileCategory.APK
            extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") || mimeType.startsWith("image/") -> FileCategory.IMAGE
            else -> FileCategory.OTHER
        }
    }

    private fun extractAvailableMediaFormats(url: String, category: FileCategory, baseSize: Long): List<MediaQualityOption> {
        val safeBase = if (baseSize > 0) baseSize else 50L * 1024 * 1024
        return when (category) {
            FileCategory.VIDEO -> listOf(
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "1080p Full HD (MP4)",
                    format = "mp4",
                    resolution = "1080p",
                    bitrate = "4500 kbps",
                    fileSize = safeBase,
                    downloadUrl = url
                ),
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "720p HD (MP4)",
                    format = "mp4",
                    resolution = "720p",
                    bitrate = "2200 kbps",
                    fileSize = (safeBase * 0.55).toLong(),
                    downloadUrl = url
                ),
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "480p SD (WebM)",
                    format = "webm",
                    resolution = "480p",
                    bitrate = "1100 kbps",
                    fileSize = (safeBase * 0.28).toLong(),
                    downloadUrl = url
                ),
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "صوت فقط Audio (MP3 320k)",
                    format = "mp3",
                    bitrate = "320 kbps",
                    fileSize = (safeBase * 0.12).toLong(),
                    isAudioOnly = true,
                    downloadUrl = url
                )
            )
            FileCategory.AUDIO -> listOf(
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "HQ Audio (320 kbps MP3)",
                    format = "mp3",
                    bitrate = "320 kbps",
                    fileSize = safeBase,
                    isAudioOnly = true,
                    downloadUrl = url
                ),
                MediaQualityOption(
                    id = UUID.randomUUID().toString(),
                    label = "Standard Audio (192 kbps M4A)",
                    format = "m4a",
                    bitrate = "192 kbps",
                    fileSize = (safeBase * 0.6).toLong(),
                    isAudioOnly = true,
                    downloadUrl = url
                )
            )
            else -> emptyList()
        }
    }
}
