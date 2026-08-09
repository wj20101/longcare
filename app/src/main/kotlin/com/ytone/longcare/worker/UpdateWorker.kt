package com.ytone.longcare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.system.SystemRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceUtils: DeviceUtils,
    private val systemRepository: SystemRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val result = systemRepository.checkVersion()) {
            is ApiResult.Success -> {
                val serverVersion = result.data
                
                // 检查平台是否匹配
                if (serverVersion.platform.lowercase() != "android") {
                    return Result.success()
                }
                
                // 获取当前应用版本
                val currentVersionCode = deviceUtils.getAppVersionCode()
                
                // WorkManager output is persisted, so the UI can recover it after lifecycle/process changes.
                if (serverVersion.versionCode.toLong() > currentVersionCode) {
                    Result.success(StartupUpdateWork.output(serverVersion))
                } else {
                    Result.success()
                }
            }
            is ApiResult.Failure -> {
                DiagnosticEventTracker.trackError(
                    category = UPDATE_DIAGNOSTIC_CATEGORY,
                    event = "version_check_failure",
                    description = "版本检查业务失败",
                    extras = mapOf(
                        "failureCode" to result.code,
                        "failureMessage" to result.message,
                    ),
                )
                Result.failure()
            }
            is ApiResult.Exception -> {
                DiagnosticEventTracker.trackError(
                    category = UPDATE_DIAGNOSTIC_CATEGORY,
                    event = "version_check_exception",
                    description = "版本检查接口异常",
                    throwable = result.exception,
                )
                if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
            }
        }
    }

    private companion object {
        const val UPDATE_DIAGNOSTIC_CATEGORY = "app_update"
        const val MAX_RETRY_COUNT = 2
    }
}
