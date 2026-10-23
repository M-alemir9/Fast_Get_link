package com.fastget.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fastget.downloader.FastGetApplication
import com.fastget.downloader.MainActivity
import com.fastget.downloader.R
import com.fastget.downloader.data.local.dao.DownloadDao
import com.fastget.downloader.data.model.DownloadStatus
import com.fastget.downloader.data.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : LifecycleService() {

    @Inject
    lateinit var repository: DownloadRepository

    @Inject
    lateinit var dao: DownloadDao

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        observeActiveDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PAUSE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_STICKY
                repository.pauseDownload(taskId)
            }
            ACTION_RESUME -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_STICKY
                repository.resumeDownload(taskId)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_STICKY
                repository.cancelDownload(taskId)
            }
        }

        return START_STICKY
    }

    private fun observeActiveDownloads() {
        lifecycleScope.launch {
            repository.allTasksFlow.collectLatest { tasks ->
                val activeTasks = tasks.filter { it.status == DownloadStatus.DOWNLOADING }
                if (activeTasks.isNotEmpty()) {
                    val primaryTask = activeTasks.first()
                    val notification = buildProgressNotification(
                        taskId = primaryTask.id,
                        fileName = primaryTask.fileName,
                        downloaded = primaryTask.downloadedBytes,
                        total = primaryTask.totalBytes,
                        activeCount = activeTasks.size
                    )
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    private fun buildProgressNotification(
        taskId: String,
        fileName: String,
        downloaded: Long,
        total: Long,
        activeCount: Int
    ): Notification {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (activeCount > 1) {
            "$fileName (+${activeCount - 1} أخرى)"
        } else {
            fileName
        }

        return NotificationCompat.Builder(this, FastGetApplication.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$progress% • ${formatBytes(downloaded)} / ${formatBytes(total)}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, total <= 0)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_pause), pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_cancel), cancelIntent)
            .build()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FastGet::DownloadWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.fastget.downloader.PAUSE"
        const val ACTION_RESUME = "com.fastget.downloader.RESUME"
        const val ACTION_CANCEL = "com.fastget.downloader.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
