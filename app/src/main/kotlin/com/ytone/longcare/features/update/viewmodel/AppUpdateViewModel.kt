package com.ytone.longcare.features.update.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
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
                        DiagnosticEventTracker.trackError(
                            category = UPDATE_DIAGNOSTIC_CATEGORY,
                            event = "apk_download_worker_failed",
                            description = "APK下载任务失败",
                            extras = mapOf(
                                "workId" to workId.toString(),
                                "error" to error,
                            ),
                        )
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0,
                            error = error ?: "安装包下载失败，请检查网络后重试"
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
                is ApkInstallUtils.LaunchResult.ManualFallback -> {
                    trackInstallIssue(
                        event = "apk_install_manual_fallback",
                        description = "APK安装需要手动兜底",
                        filePath = filePath,
                        message = result.message,
                    )
                    pendingInstallFilePath = null
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        hasPendingInstall = false
                    )
                }
                is ApkInstallUtils.LaunchResult.Failed -> {
                    trackInstallIssue(
                        event = "apk_install_launch_failed",
                        description = "APK安装启动失败",
                        filePath = filePath,
                        message = result.message,
                    )
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
                is ApkInstallUtils.LaunchResult.ManualFallback -> {
                    trackInstallIssue(
                        event = "apk_install_permission_manual_fallback",
                        description = "APK安装权限设置需要手动兜底",
                        filePath = filePath,
                        message = result.message,
                    )
                    pendingInstallFilePath = null
                    _uiState.value = _uiState.value.copy(
                        hasPendingInstall = false,
                        error = result.message
                    )
                }
                is ApkInstallUtils.LaunchResult.Failed -> {
                    trackInstallIssue(
                        event = "apk_install_permission_failed",
                        description = "APK安装权限设置打开失败",
                        filePath = filePath,
                        message = result.message,
                    )
                    pendingInstallFilePath = null
                    handleManualInstallFallback(filePath, result.message)
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
                    is ApkInstallUtils.LaunchResult.ManualFallback -> {
                        trackInstallIssue(
                            event = "apk_pending_install_manual_fallback",
                            description = "待安装APK需要手动兜底",
                            filePath = filePath,
                            message = result.message,
                        )
                        pendingInstallFilePath = null
                        _uiState.value = _uiState.value.copy(
                            hasPendingInstall = false,
                            error = result.message
                        )
                    }
                    is ApkInstallUtils.LaunchResult.Failed -> {
                        trackInstallIssue(
                            event = "apk_pending_install_failed",
                            description = "待安装APK启动安装失败",
                            filePath = filePath,
                            message = result.message,
                        )
                        pendingInstallFilePath = null
                        _uiState.value = _uiState.value.copy(
                            hasPendingInstall = false,
                            error = result.message
                        )
                    }
                }
            } else {
                pendingInstallFilePath = null
                handleManualInstallFallback(filePath, "未获得安装未知来源应用权限")
            }
        }
    }

    private fun handleManualInstallFallback(filePath: String, failureMessage: String) {
        when (val fallback = ApkInstallUtils.openApkForManualInstall(context, filePath)) {
            ApkInstallUtils.LaunchResult.Launched -> {
                _uiState.value = _uiState.value.copy(
                    hasPendingInstall = false,
                    error = "已打开安装包，请在系统界面中手动完成安装"
                )
            }
            is ApkInstallUtils.LaunchResult.ManualFallback -> {
                trackInstallIssue(
                    event = "apk_manual_install_fallback",
                    description = "APK手动安装兜底已触发",
                    filePath = filePath,
                    message = fallback.message,
                )
                _uiState.value = _uiState.value.copy(
                    hasPendingInstall = false,
                    error = fallback.message
                )
            }
            is ApkInstallUtils.LaunchResult.Failed -> {
                trackInstallIssue(
                    event = "apk_manual_install_failed",
                    description = "APK手动安装兜底失败",
                    filePath = filePath,
                    message = "$failureMessage；${fallback.message}",
                )
                _uiState.value = _uiState.value.copy(
                    hasPendingInstall = false,
                    error = "$failureMessage；${fallback.message}"
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

    private fun trackInstallIssue(
        event: String,
        description: String,
        filePath: String,
        message: String,
    ) {
        val file = java.io.File(filePath)
        DiagnosticEventTracker.trackError(
            category = UPDATE_DIAGNOSTIC_CATEGORY,
            event = event,
            description = description,
            extras = mapOf(
                "message" to message,
                "fileExists" to file.exists(),
                "fileSize" to file.takeIf { it.exists() }?.length(),
            ),
        )
    }

    private companion object {
        const val UPDATE_DIAGNOSTIC_CATEGORY = "app_update"
    }
}

data class AppUpdateUiState(
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val error: String? = null,
    val hasPendingInstall: Boolean = false
)
