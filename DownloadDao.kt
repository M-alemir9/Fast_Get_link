package com.fastget.downloader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fastget.downloader.data.local.entity.DownloadChunkEntity
import com.fastget.downloader.data.local.entity.DownloadTaskEntity
import com.fastget.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :taskId")
    fun getTaskFlowById(taskId: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: DownloadStatus)

    @Query("UPDATE download_tasks SET downloadedBytes = :bytes WHERE id = :taskId")
    suspend fun updateTaskProgress(taskId: String, bytes: Long)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    // Chunks operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DownloadChunkEntity>)

    @Query("SELECT * FROM download_chunks WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    suspend fun getChunksForTask(taskId: String): List<DownloadChunkEntity>

    @Query("SELECT * FROM download_chunks WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    fun getChunksFlowForTask(taskId: String): Flow<List<DownloadChunkEntity>>

    @Query("UPDATE download_chunks SET downloadedBytes = :downloaded, isCompleted = :completed WHERE id = :chunkId")
    suspend fun updateChunkProgress(chunkId: String, downloaded: Long, completed: Boolean)

    @Query("DELETE FROM download_chunks WHERE taskId = :taskId")
    suspend fun deleteChunksForTask(taskId: String)

    @Transaction
    suspend fun insertTaskWithChunks(task: DownloadTaskEntity, chunks: List<DownloadChunkEntity>) {
        insertTask(task)
        insertChunks(chunks)
    }
}
