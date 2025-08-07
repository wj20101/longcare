package com.ytone.longcare.features.update.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ytone.longcare.api.response.AppVersionModel
import com.ytone.longcare.worker.DownloadWorker
import com.ytone.longcare.common.utils.ApkInstallUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var currentWorkId: UUID? = null
    private val workManager = WorkManager.getInstance(context)

    fun setAppVersionModel(appVersionModel: AppVersionModel) {
        _uiState.value = _uiState.value.copy(
            appVersionModel = appVersionModel,
            showDialog = true
        )
    }

    fun startDownload() {
        val appVersionModel = _uiState.value.appVersionModel ?: return
        
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
            downloadProgress = 0
        )

        // 监听下载进度
        observeDownloadProgress(downloadWorkRequest.id)
    }

    private fun observeDownloadProgress(workId: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(workId).observeForever { workInfo ->
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
                            downloadedFilePath = filePath,
                            showDialog = false
                        )
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
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0
                        )
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
                downloadProgress = 0
            )
        }
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = false,
            appVersionModel = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun installApk(filePath: String) {
        if (ApkInstallUtils.canInstallApk(context)) {
            ApkInstallUtils.installApk(context, filePath)
        } else {
            // 需要权限时，可以提示用户或直接跳转设置
            ApkInstallUtils.requestInstallPermission(context)
        }
    }
}

data class AppUpdateUiState(
    val appVersionModel: AppVersionModel? = null,
    val showDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedFilePath: String? = null,
    val error: String? = null
)