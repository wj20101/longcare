package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 销售、拍照保留独立单次预算；取消和重试只释放共享 provider 中的自身句柄。 */
@Singleton
class SingleAmapLocationSource @Inject constructor(
    private val clientFactory: AmapClientProvider,
    private val amapApiKeyProvider: AmapApiKeyProvider,
) {
    private val singleRequest = SingleLocationRequest(SystemClock::elapsedRealtime)

    private fun buildSingleLocationOption(remainingMs: Long) = newAmapLocationOption().apply {
        isOnceLocation = true
        isOnceLocationLatest = true
        // 两次采集共用总预算；不以默认 30 秒或新一轮 15 秒延长等待。
        httpTimeOut = remainingMs.coerceIn(1_000L, SingleLocationRequest.TIMEOUT_MS)
    }

    suspend fun acquireCurrentLocation(): LocationAcquisition {
        val requestId = UUID.randomUUID().toString()
        return singleRequest.acquire(retryIf = { failure ->
            failure.location?.isMock != true &&
                failure.location?.errorCode != AMapLocation.ERROR_CODE_FAILURE_SIMULATION_LOCATION &&
                failure.reason == LocationFailure.QUALITY
        }) { _, deadline -> getIsolatedLocation(deadline, requestId) }
    }

    private suspend fun getIsolatedLocation(
        deadlineElapsedRealtime: Long,
        requestId: String,
    ): LocationAcquisition = withContext(Dispatchers.Main.immediate) {
        fun deadlineReached() = SystemClock.elapsedRealtime() >= deadlineElapsedRealtime
        if (deadlineReached()) return@withContext LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        val keyResult = runCatching { amapApiKeyProvider.getAmapApiKey() }
        currentCoroutineContext().ensureActive()
        keyResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (deadlineReached()) return@withContext LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        val apiKey = keyResult.getOrThrow()?.takeIf { it.isNotBlank() }
            ?: return@withContext LocationAcquisition.Failure(LocationFailure.CONFIGURATION)
        currentCoroutineContext().ensureActive()
        var client: AmapClientHandle? = null
        var listener: AMapLocationListener? = null
        try {
            val isolatedClient = clientFactory.create(apiKey)
            client = isolatedClient
            if (deadlineReached()) return@withContext LocationAcquisition.Failure(LocationFailure.TIMEOUT)
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                listener = AMapLocationListener { location ->
                    if (finished.compareAndSet(false, true)) {
                        val result = if (deadlineReached()) {
                            LocationAcquisition.Failure(LocationFailure.TIMEOUT)
                        } else if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                            val sample = location.toSample(requestId)
                            val rejection = LocationQuality.rejection(sample, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                            if (rejection == null) {
                                LocationAcquisition.Success(sample)
                            } else {
                                LocationAcquisition.Failure(LocationFailure.QUALITY, sample)
                            }
                        } else {
                            LocationAcquisition.Failure(locationFailure(location?.errorCode), location?.toSample(requestId))
                        }
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation { finished.set(true) }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                isolatedClient.setLocationListener(requireNotNull(listener))
                if (!continuation.isActive) return@suspendCancellableCoroutine
                if (deadlineReached()) {
                    if (finished.compareAndSet(false, true))
                        continuation.resume(LocationAcquisition.Failure(LocationFailure.TIMEOUT))
                    return@suspendCancellableCoroutine
                }
                isolatedClient.setLocationOption(buildSingleLocationOption(deadlineElapsedRealtime - SystemClock.elapsedRealtime()))
                if (!continuation.isActive) return@suspendCancellableCoroutine
                if (deadlineReached()) {
                    if (finished.compareAndSet(false, true))
                        continuation.resume(LocationAcquisition.Failure(LocationFailure.TIMEOUT))
                    return@suspendCancellableCoroutine
                }
                isolatedClient.startLocation(deadlineElapsedRealtime)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            LocationAcquisition.Failure(LocationFailure.PERMISSION)
        } catch (error: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                throwable = error,
            )
            LocationAcquisition.Failure(LocationFailure.UNAVAILABLE)
        } finally {
            // 各释放步骤独立执行，注销监听失败也不能跳过销毁；只触及本次客户端。
            listOf<() -> Unit>(
                { listener?.let { client?.unRegisterLocationListener(it) } },
                { client?.stopLocation() },
                { client?.onDestroy() },
            ).forEach { cleanup ->
                runCatching(cleanup).onFailure {
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                        throwable = it,
                    )
                }
            }
        }
    }
}
