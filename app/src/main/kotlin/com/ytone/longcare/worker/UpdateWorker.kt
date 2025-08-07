package com.ytone.longcare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
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
    private val eventBus: AppEventBus
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val result = systemRepository.checkVersion()) {
            is ApiResult.Success -> {
                val serverVersion = result.data
                
                // 检查平台是否匹配
                if (serverVersion.platform.lowercase() != "android") {
                    return Result.success() // 不是Android平台的更新，直接成功返回
                }
                
                // 获取当前应用版本
                val currentVersionCode = deviceUtils.getAppVersionCode()
                
                // 比较版本号，只有服务器版本更高时才发送更新事件
                if (serverVersion.versionCode.toLong() > currentVersionCode) {
                    eventBus.send(AppEvent.AppUpdate(serverVersion))
                }
                
                Result.success()
            }
            is ApiResult.Failure -> {
                Result.failure()
            }
            is ApiResult.Exception -> {
                Result.failure()
            }
        }
    }
}