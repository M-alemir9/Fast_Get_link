package com.fastget.downloader.ui.viewmodel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastget.downloader.data.local.entity.DownloadTaskEntity
import com.fastget.downloader.data.model.DownloadStatus
import com.fastget.downloader.data.model.HeadInspectionResult
import com.fastget.downloader.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class FilterTab {
    ALL,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTaskEntity>> = repository.allTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(FilterTab.ALL)
    val selectedTab: StateFlow<FilterTab> = _selectedTab.asStateFlow()

    private val _inspectionResult = MutableStateFlow<HeadInspectionResult?>(null)
    val inspectionResult: StateFlow<HeadInspectionResult?> = _inspectionResult.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _inspectionError = MutableStateFlow<String?>(null)
    val inspectionError: StateFlow<String?> = _inspectionError.asStateFlow()

    fun selectTab(tab: FilterTab) {
        _selectedTab.value = tab
    }

    fun inspectUrl(url: String) {
        viewModelScope.launch {
            _isInspecting.value = true
            _inspectionError.value = null
            _inspectionResult.value = null

            val result = repository.inspectUrl(url)
            result.onSuccess { info ->
                _inspectionResult.value = info
            }.onFailure { error ->
                _inspectionError.value = error.localizedMessage ?: "Failed to inspect URL"
            }
            _isInspecting.value = false
        }
    }

    fun clearInspection() {
        _inspectionResult.value = null
        _inspectionError.value = null
        _isInspecting.value = false
    }

    fun startDownload(
        url: String,
        fileName: String,
        totalBytes: Long,
        supportsRange: Boolean,
        chunkCount: Int,
        category: com.fastget.downloader.data.model.FileCategory,
        mimeType: String
    ) {
        viewModelScope.launch {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            downloadDir.mkdirs()

            val destinationFile = File(downloadDir, fileName)

            val newTask = DownloadTaskEntity(
                id = UUID.randomUUID().toString(),
                url = url,
                fileName = fileName,
                destinationPath = destinationFile.absolutePath,
                totalBytes = totalBytes,
                downloadedBytes = 0L,
                status = DownloadStatus.DOWNLOADING,
                category = category,
                mimeType = mimeType,
                supportsRange = supportsRange,
                chunkCount = if (supportsRange) chunkCount else 1,
                speedLimitBytesPerSec = 0L
            )

            repository.addTask(newTask)
            clearInspection()
        }
    }

    fun pauseDownload(taskId: String) = repository.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = repository.resumeDownload(taskId)
    fun cancelDownload(taskId: String) = repository.cancelDownload(taskId)
}
