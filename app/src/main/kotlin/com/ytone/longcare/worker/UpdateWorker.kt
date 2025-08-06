package com.ytone.longcare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.system.SystemRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val systemRepository: SystemRepository,
    private val eventBus: AppEventBus
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val result = systemRepository.checkVersion()) {
            is ApiResult.Success -> {
                eventBus.send(AppEvent.AppUpdate(result.data))
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