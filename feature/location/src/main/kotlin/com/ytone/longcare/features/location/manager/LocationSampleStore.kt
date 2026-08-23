package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 进程内定位样本中心。
 *
 * 只保存最近一次定位供短时复用，并向当前订单上报任务发送实时样本；不保存订单状态、
 * 不统计历史数据，也不会为后续会话缓存待上传位置。
 */
@Singleton
class LocationSampleStore @Inject constructor() {
    @Volatile
    private var latestSample: TimedLocation? = null

    private val _continuousLocations = MutableSharedFlow<LocationResult>(
        replay = 0,
        extraBufferCapacity = LATEST_SAMPLE_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val continuousLocations: SharedFlow<LocationResult> = _continuousLocations.asSharedFlow()

    internal fun record(location: LocationResult) {
        val receivedAt = System.currentTimeMillis()
        latestSample = TimedLocation(
            location = location,
            capturedAt = location.locationTime.takeIf { it > 0L } ?: receivedAt,
        )
    }

    internal suspend fun publish(location: LocationResult) {
        record(location)
        _continuousLocations.emit(location)
    }

    fun getValidLocation(
        maxAgeMs: Long = LocationFacade.DEFAULT_LOCATION_CACHE_MAX_AGE_MS,
    ): LocationResult? {
        val sample = latestSample ?: return null
        val now = System.currentTimeMillis()
        if (
            sample.capturedAt > now + MAX_FUTURE_LOCATION_SKEW_MS ||
            now - sample.capturedAt > maxAgeMs
        ) {
            return null
        }
        return sample.location
    }

    private data class TimedLocation(
        val location: LocationResult,
        val capturedAt: Long,
    )

    private companion object {
        const val MAX_FUTURE_LOCATION_SKEW_MS = 2L * 60 * 1_000
        const val LATEST_SAMPLE_BUFFER_SIZE = 1
    }
}
