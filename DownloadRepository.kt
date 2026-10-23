package com.fastget.downloader.data.repository

import com.fastget.downloader.data.engine.DownloadEngine
import com.fastget.downloader.data.local.dao.DownloadDao
import com.fastget.downloader.data.local.entity.DownloadChunkEntity
import com.fastget.downloader.data.local.entity.DownloadTaskEntity
import com.fastget.downloader.data.network.UrlInspector
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    private val engine: DownloadEngine,
    private val inspector: UrlInspector
) {
    val allTasksFlow: Flow<List<DownloadTaskEntity>> = dao.getAllTasksFlow()

    fun getTaskFlow(taskId: String): Flow<DownloadTaskEntity?> = dao.getTaskFlowById(taskId)

    fun getChunksFlow(taskId: String): Flow<List<DownloadChunkEntity>> = dao.getChunksFlowForTask(taskId)

    suspend fun inspectUrl(url: String) = inspector.inspectUrl(url)

    suspend fun addTask(task: DownloadTaskEntity) {
        dao.insertTask(task)
        engine.startDownload(task.id)
    }

    fun pauseDownload(taskId: String) {
        engine.pauseDownload(taskId)
    }

    fun resumeDownload(taskId: String) {
        engine.startDownload(taskId)
    }

    fun cancelDownload(taskId: String) {
        engine.cancelDownload(taskId)
    }

    fun setSpeedLimit(taskId: String, bytesPerSec: Long) {
        engine.setSpeedLimit(taskId, bytesPerSec)
    }

    suspend fun retryDownload(taskId: String) {
        engine.startDownload(taskId)
    }
}
