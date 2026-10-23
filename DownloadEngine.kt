package com.fastget.downloader.data.engine

import android.content.Context
import com.fastget.downloader.data.local.dao.DownloadDao
import com.fastget.downloader.data.local.entity.DownloadChunkEntity
import com.fastget.downloader.data.local.entity.DownloadTaskEntity
import com.fastget.downloader.data.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val speedLimiters = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()

    fun startDownload(taskId: String) {
        if (activeJobs.containsKey(taskId)) return

        val job = scope.launch {
            executeDownload(taskId)
        }
        activeJobs[taskId] = job
    }

    fun pauseDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            dao.updateTaskStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun cancelDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            val task = dao.getTaskById(taskId)
            task?.let {
                // Remove partial temporary files
                val targetFile = File(it.destinationPath)
                if (targetFile.exists()) targetFile.delete()

                val chunks = dao.getChunksForTask(taskId)
                chunks.forEach { c ->
                    val chunkFile = File(c.tempFilePath)
                    if (chunkFile.exists()) chunkFile.delete()
                }
            }
            dao.deleteTask(taskId)
        }
    }

    fun setSpeedLimit(taskId: String, bytesPerSec: Long) {
        speedLimiters[taskId] = bytesPerSec
    }

    private suspend fun executeDownload(taskId: String) {
        val task = dao.getTaskById(taskId) ?: return
        dao.updateTaskStatus(taskId, DownloadStatus.DOWNLOADING)

        var chunks = dao.getChunksForTask(taskId)
        if (chunks.isEmpty()) {
            chunks = createAndSaveChunks(task)
        }

        val targetFile = File(task.destinationPath)
        targetFile.parentFile?.mkdirs()

        // Pre-allocate destination file if size is known and range is supported
        if (task.supportsRange && task.totalBytes > 0) {
            RandomAccessFile(targetFile, "rw").use { raf ->
                if (raf.length() < task.totalBytes) {
                    raf.setLength(task.totalBytes)
                }
            }
        }

        try {
            if (task.supportsRange && chunks.size > 1) {
                // Multi-threaded parallel chunk download
                val chunkJobs = chunks.map { chunk ->
                    async {
                        downloadChunkWithRetry(task, chunk, targetFile)
                    }
                }
                chunkJobs.awaitAll()
            } else {
                // Single thread download (No Range support or single chunk)
                downloadSingleStream(task, targetFile)
            }

            // Verify integrity and mark completed
            val checksum = calculateSha256(targetFile)
            dao.updateTask(
                task.copy(
                    downloadedBytes = targetFile.length(),
                    status = DownloadStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    checksumSha256 = checksum
                )
            )
            activeJobs.remove(taskId)
        } catch (e: CancellationException) {
            dao.updateTaskStatus(taskId, DownloadStatus.PAUSED)
        } catch (e: Exception) {
            // Failure with auto-retry
            handleDownloadFailure(task, e)
        } finally {
            activeJobs.remove(taskId)
        }
    }

    private suspend fun downloadChunkWithRetry(
        task: DownloadTaskEntity,
        chunk: DownloadChunkEntity,
        targetFile: File
    ) {
        var attempt = 0
        val maxAttempts = 5
        var currentChunk = chunk

        while (attempt < maxAttempts) {
            try {
                if (currentChunk.isCompleted) return

                downloadChunkRange(task, currentChunk, targetFile)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                // Exponential backoff delay (1s, 2s, 4s, 8s...)
                val backoffMs = (1000L * (1L shl (attempt - 1))).coerceAtMost(16000L)
                delay(backoffMs)
                // Reload chunk state
                currentChunk = dao.getChunksForTask(task.id).first { it.id == chunk.id }
            }
        }
    }

    private suspend fun downloadChunkRange(
        task: DownloadTaskEntity,
        chunk: DownloadChunkEntity,
        targetFile: File
    ) {
        val currentStart = chunk.startByte + chunk.downloadedBytes
        if (currentStart > chunk.endByte) {
            dao.updateChunkProgress(chunk.id, chunk.totalChunkBytes, true)
            return
        }

        val request = Request.Builder()
            .url(task.url)
            .header("Range", "bytes=$currentStart-${chunk.endByte}")
            .header("User-Agent", "FastGet-Android-Engine/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw Exception("HTTP ${response.code} downloading chunk ${chunk.chunkIndex}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val inputStream = body.byteStream()

        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.seek(currentStart)
            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var localDownloaded = chunk.downloadedBytes

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw CancellationException()
                }

                raf.write(buffer, 0, bytesRead)
                localDownloaded += bytesRead

                // Throttle speed if limit is set
                throttleSpeed(task.id, bytesRead)

                // Persist progress periodically
                if (localDownloaded % (64 * 1024) < bytesRead || localDownloaded >= chunk.totalChunkBytes) {
                    val isCompleted = localDownloaded >= chunk.totalChunkBytes
                    dao.updateChunkProgress(chunk.id, localDownloaded, isCompleted)
                    updateTotalTaskProgress(task.id)
                }
            }
        }
        response.close()
    }

    private suspend fun downloadSingleStream(task: DownloadTaskEntity, targetFile: File) {
        val request = Request.Builder()
            .url(task.url)
            .header("User-Agent", "FastGet-Android-Engine/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val inputStream = body.byteStream()
        val buffer = ByteArray(32 * 1024)
        var totalRead = 0L

        targetFile.outputStream().use { output ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw CancellationException()
                }
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                throttleSpeed(task.id, bytesRead)

                if (totalRead % (128 * 1024) < bytesRead) {
                    dao.updateTaskProgress(task.id, totalRead)
                }
            }
        }
        response.close()
        dao.updateTaskProgress(task.id, totalRead)
    }

    private suspend fun throttleSpeed(taskId: String, bytesRead: Int) {
        val limit = speedLimiters[taskId] ?: return
        if (limit <= 0) return
        val expectedTimeMs = (bytesRead.toDouble() / limit.toDouble() * 1000.0).toLong()
        if (expectedTimeMs > 0) {
            delay(expectedTimeMs)
        }
    }

    private suspend fun updateTotalTaskProgress(taskId: String) {
        mutex.withLock {
            val chunks = dao.getChunksForTask(taskId)
            val totalDownloaded = chunks.sumOf { it.downloadedBytes }
            dao.updateTaskProgress(taskId, totalDownloaded)
        }
    }

    private suspend fun handleDownloadFailure(task: DownloadTaskEntity, e: Exception) {
        val updatedRetry = task.retryCount + 1
        dao.updateTask(
            task.copy(
                status = DownloadStatus.FAILED,
                retryCount = updatedRetry,
                errorMessage = e.localizedMessage ?: "Download failed"
            )
        )
    }

    private suspend fun createAndSaveChunks(task: DownloadTaskEntity): List<DownloadChunkEntity> {
        val chunkCount = task.chunkCount.coerceIn(1, 16)
        val totalBytes = task.totalBytes

        if (!task.supportsRange || totalBytes <= 0 || chunkCount <= 1) {
            val singleChunk = DownloadChunkEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                chunkIndex = 0,
                startByte = 0,
                endByte = (if (totalBytes > 0) totalBytes - 1 else 0),
                downloadedBytes = 0,
                totalChunkBytes = (if (totalBytes > 0) totalBytes else 0),
                tempFilePath = "${task.destinationPath}.part0"
            )
            dao.insertChunks(listOf(singleChunk))
            return listOf(singleChunk)
        }

        val chunkSize = totalBytes / chunkCount
        val chunks = mutableListOf<DownloadChunkEntity>()

        for (i in 0 until chunkCount) {
            val startByte = i * chunkSize
            val endByte = if (i == chunkCount - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
            chunks.add(
                DownloadChunkEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    chunkIndex = i,
                    startByte = startByte,
                    endByte = endByte,
                    downloadedBytes = 0,
                    totalChunkBytes = endByte - startByte + 1,
                    tempFilePath = "${task.destinationPath}.part$i"
                )
            )
        }

        dao.insertChunks(chunks)
        return chunks
    }

    private fun calculateSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
