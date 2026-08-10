package com.ytone.longcare.worker

import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.model.AppVersionModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

internal object StartupUpdateWork {
    const val UNIQUE_WORK_NAME = "startup_update_worker"

    private const val KEY_HAS_UPDATE = "has_update"
    private const val KEY_VERSION_CODE = "version_code"
    private const val KEY_VERSION_NAME = "version_name"
    private const val KEY_UPDATE_TYPE = "update_type"
    private const val KEY_REMARKS = "remarks"
    private const val KEY_PLATFORM = "platform"
    private const val KEY_DOWNLOAD_URL = "download_url"

    fun output(appVersion: AppVersionModel): Data = workDataOf(
        KEY_HAS_UPDATE to true,
        KEY_VERSION_CODE to appVersion.versionCode,
        KEY_VERSION_NAME to appVersion.versionName,
        KEY_UPDATE_TYPE to appVersion.upType,
        KEY_REMARKS to appVersion.remarks.take(MAX_REMARKS_LENGTH),
        KEY_PLATFORM to appVersion.platform,
        KEY_DOWNLOAD_URL to appVersion.downUrl,
    )

    fun read(data: Data): AppVersionModel? {
        if (!data.getBoolean(KEY_HAS_UPDATE, false)) return null
        return AppVersionModel(
            versionCode = data.getInt(KEY_VERSION_CODE, 0),
            versionName = data.getString(KEY_VERSION_NAME).orEmpty(),
            upType = data.getInt(KEY_UPDATE_TYPE, 1),
            remarks = data.getString(KEY_REMARKS).orEmpty(),
            platform = data.getString(KEY_PLATFORM).orEmpty(),
            downUrl = data.getString(KEY_DOWNLOAD_URL).orEmpty(),
        )
    }

    private const val MAX_REMARKS_LENGTH = 4_096
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class StartupUpdateWorkObserver @Inject constructor(
    private val workManager: WorkManager,
    private val deviceUtils: DeviceUtils,
) {
    private val activeWorkId = MutableStateFlow<java.util.UUID?>(null)

    val availableUpdate: Flow<AppVersionModel?> =
        activeWorkId
            .filterNotNull()
            .flatMapLatest(workManager::getWorkInfoByIdFlow)
            .map { workInfo ->
                if (workInfo?.state != WorkInfo.State.SUCCEEDED) {
                    return@map null
                }
                StartupUpdateWork.read(workInfo.outputData)
                    ?.takeIf { it.versionCode.toLong() > deviceUtils.getAppVersionCode() }
            }
            .distinctUntilChanged()

    fun enqueueLatestCheck() {
        val request =
            OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        workManager.enqueueUniqueWork(
            StartupUpdateWork.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        activeWorkId.value = request.id
    }
}
