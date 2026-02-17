package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.model.LocationUploadStatus
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.location.LocationUploadQueueRepository
import com.ytone.longcare.features.location.manager.LocationStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 位置上报任务管理器。
 * 只关注“取定位并上报”这件事，不负责定位服务保活的具体实现。
 */
@Singleton
class LocationReportingManager @Inject constructor(
    private val locationFacade: LocationFacade,
    private val locationStateManager: LocationStateManager,
    private val locationRepository: LocationRepository,
    private val locationUploadQueueRepository: LocationUploadQueueRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentTrackingOrderKey = MutableStateFlow<OrderKey?>(null)
    val currentTrackingOrderKey: StateFlow<OrderKey?> = _currentTrackingOrderKey.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var reportingJob: Job? = null
    private var currentOwner: String? = null
    private val uploadMutex = Mutex()

    companion object {
        private const val MAX_UPLOAD_BATCH = 30
        private const val SUCCESS_RETENTION_MS = 24 * 60 * 60 * 1000L
    }

    fun startReporting(orderKey: OrderKey) {
        val sameTaskRunning = _isTracking.value &&
            _currentTrackingOrderKey.value?.orderId == orderKey.orderId &&
            reportingJob?.isActive == true
        if (sameTaskRunning) return

        stopReporting()

        _currentTrackingOrderKey.value = orderKey
        _isTracking.value = true
        locationStateManager.updateTrackingState(true)

        val owner = buildOwner(orderKey)
        currentOwner = owner
        locationFacade.acquireKeepAlive(owner)

        reportingJob = scope.launch {
            try {
                flushUploadQueue()
                locationFacade.observeLocations().collect { location ->
                    enqueueLocation(orderKey.orderId, location)
                    flushUploadQueue()
                }
            } catch (_: CancellationException) {
                logI("位置上报任务已取消")
            } catch (e: Exception) {
                logE("位置上报任务异常终止: ${e.message}")
            } finally {
                logI("位置上报任务结束")
            }
        }
    }

    fun stopReporting() {
        reportingJob?.cancel()
        reportingJob = null

        currentOwner?.let { owner ->
            locationFacade.releaseKeepAlive(owner)
        }
        currentOwner = null

        _isTracking.value = false
        _currentTrackingOrderKey.value = null
        locationStateManager.updateTrackingState(false)
    }

    fun forceStopReporting() {
        stopReporting()
    }

    private suspend fun enqueueLocation(orderId: Long, location: LocationResult) {
        try {
            locationUploadQueueRepository.insert(
                OrderLocationEntity(
                    orderId = orderId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    provider = location.provider,
                    uploadStatus = LocationUploadStatus.PENDING.value,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("写入定位上报队列失败: ${e.message}")
        }
    }

    private suspend fun flushUploadQueue() {
        uploadMutex.withLock {
            val queue = locationUploadQueueRepository.getUploadQueue(
                statuses = listOf(
                    LocationUploadStatus.PENDING.value,
                    LocationUploadStatus.FAILED.value
                ),
                limit = MAX_UPLOAD_BATCH
            )

            queue.forEach { pending ->
                uploadSingle(pending)
            }

            cleanupOldSuccess()
        }
    }

    private suspend fun uploadSingle(pending: OrderLocationEntity) {
        try {
            when (val apiResult = locationRepository.addPosition(
                orderId = pending.orderId,
                latitude = pending.latitude,
                longitude = pending.longitude
            )) {
                is ApiResult.Success -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.SUCCESS.value)
                    logI("位置上报成功 (orderId=${pending.orderId}, id=${pending.id})")
                }
                is ApiResult.Failure -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
                    logE("位置上报业务失败 (id=${pending.id}): ${apiResult.message}")
                }
                is ApiResult.Exception -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
                    logE("位置上报异常 (id=${pending.id}): ${apiResult.exception.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("上传位置过程发生严重错误: ${e.message}")
            try {
                locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun cleanupOldSuccess() {
        try {
            val deleted = locationUploadQueueRepository.deleteByStatusBefore(
                status = LocationUploadStatus.SUCCESS.value,
                beforeTime = System.currentTimeMillis() - SUCCESS_RETENTION_MS
            )
            if (deleted > 0) {
                logI("清理历史成功定位记录: $deleted 条")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("清理历史成功定位记录失败: ${e.message}")
        }
    }

    private fun buildOwner(orderKey: OrderKey): String {
        return "location_report_${orderKey.orderId}"
    }
}
