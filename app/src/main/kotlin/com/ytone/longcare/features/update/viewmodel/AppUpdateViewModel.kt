package com.ytone.longcare.features.update.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.worker.DownloadWorker
import com.ytone.longcare.common.utils.ApkInstallUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var currentWorkId: UUID? = null
    private var downloadObservationJob: Job? = null
    private val workManager = WorkManager.getInstance(context)
    private var pendingInstallFilePath: String? = null

    fun onDialogPresented() {
        if (_uiState.value.isDownloading || _uiState.value.hasPendingInstall || currentWorkId != null) {
            return
        }

        _uiState.value = _uiState.value.copy(
            downloadProgress = 0,
            error = null
        )
    }

    fun startDownload(appVersionModel: AppVersionModel) {
        pendingInstallFilePath = null
        downloadObservationJob?.cancel()
        
        val data = Data.Builder()
            .putString(DownloadWorker.KEY_URL, appVersionModel.downUrl)
            .putString(DownloadWorker.KEY_FILE_NAME, "longcare_${appVersionModel.versionName}.apk")
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        currentWorkId = downloadWorkRequest.id
        workManager.enqueue(downloadWorkRequest)

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0,
            error = null,
            hasPendingInstall = false
        )

        // 监听下载进度
        observeDownloadProgress(downloadWorkRequest.id)
    }

    private fun observeDownloadProgress(workId: UUID) {
        downloadObservationJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                        _uiState.value = _uiState.value.copy(
                            isDownloading = true,
                            downloadProgress = progress
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val filePath = workInfo.outputData.getString(DownloadWorker.KEY_FILE_PATH)
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 100,
                            error = null
                        )
                        clearCurrentWorkTracking(workId)
                        // 可以在这里触发安装
                        filePath?.let { installApk(it) }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString(DownloadWorker.KEY_ERROR)
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0,
                            error = error ?: "下载失败"
                        )
                        clearCurrentWorkTracking(workId)
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0
                        )
                        clearCurrentWorkTracking(workId)
                    }
                    else -> {
                        // 其他状态暂不处理
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        currentWorkId?.let { workId ->
            workManager.cancelWorkById(workId)
            _uiState.value = _uiState.value.copy(
                isDownloading = false,
                downloadProgress = 0,
                hasPendingInstall = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun installApk(filePath: String) {
        if (ApkInstallUtils.canInstallApk(context)) {
            when (val result = ApkInstallUtils.installApk(context, filePath)) {
                ApkInstallUtils.LaunchResult.Launched -> {
                    _uiState.value = _uiState.value.copy(error = null)
                }
                is ApkInstallUtils.LaunchResult.Failed -> {
                    pendingInstallFilePath = null
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        hasPendingInstall = false
                    )
                }
            }
        } else {
            when (val result = ApkInstallUtils.requestInstallPermission(context)) {
                ApkInstallUtils.LaunchResult.Launched -> {
                    pendingInstallFilePath = filePath
                    _uiState.value = _uiState.value.copy(
                        hasPendingInstall = true,
                        error = null
                    )
                }
                is ApkInstallUtils.LaunchResult.Failed -> {
                    pendingInstallFilePath = null
                    _uiState.value = _uiState.value.copy(
                        hasPendingInstall = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /**
     * 检查权限并安装待安装的APK
     * 当用户从设置页面返回时调用
     */
    fun checkPermissionAndInstall() {
        pendingInstallFilePath?.let { filePath ->
            if (ApkInstallUtils.canInstallApk(context)) {
                when (val result = ApkInstallUtils.installApk(context, filePath)) {
                    ApkInstallUtils.LaunchResult.Launched -> {
                        pendingInstallFilePath = null
                        _uiState.value = _uiState.value.copy(
                            hasPendingInstall = false,
                            error = null
                        )
                    }
                    is ApkInstallUtils.LaunchResult.Failed -> {
                        pendingInstallFilePath = null
                        _uiState.value = _uiState.value.copy(
                            hasPendingInstall = false,
                            error = result.message
                        )
                    }
                }
            } else {
                pendingInstallFilePath = null
                _uiState.value = _uiState.value.copy(
                    hasPendingInstall = false,
                    error = "请允许安装未知来源应用后重试"
                )
            }
        }
    }

    private fun clearCurrentWorkTracking(workId: UUID) {
        if (currentWorkId == workId) {
            currentWorkId = null
        }
        downloadObservationJob?.cancel()
        downloadObservationJob = null
    }
}

data class AppUpdateUiState(
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val error: String? = null,
    val hasPendingInstall: Boolean = false
)
