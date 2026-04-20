package com.ytone.longcare.features.location.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定位保活生命周期管理器（独立于具体业务）。
 * 使用 owner 引用计数，避免多个业务互相抢占定位保活。
 */
@Singleton
class LocationKeepAliveManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationStateManager: LocationStateManager
) {
    private val lock = Any()
    private val activeOwners = linkedSetOf<String>()
    private var cacheJob: Job? = null

    fun acquire(owner: String) {
        if (owner.isBlank()) return

        var shouldStart = false
        synchronized(lock) {
            if (activeOwners.add(owner)) {
                logI("定位保活 +1: $owner, active=${activeOwners.size}")
                shouldStart = activeOwners.size == 1
            }
        }

        if (shouldStart) {
            startForegroundKeepAlive(owner)
            startCacheCollector()
        }
    }

    fun release(owner: String) {
        if (owner.isBlank()) return

        var shouldStop = false
        synchronized(lock) {
            if (activeOwners.remove(owner)) {
                logI("定位保活 -1: $owner, active=${activeOwners.size}")
                shouldStop = activeOwners.isEmpty()
            }
        }

        if (shouldStop) {
            stopCacheCollector()
            stopForegroundKeepAlive()
        }
    }

    fun forceReleaseAll() {
        val shouldStop: Boolean
        synchronized(lock) {
            shouldStop = activeOwners.isNotEmpty()
            activeOwners.clear()
        }
        if (shouldStop) {
            stopCacheCollector()
            stopForegroundKeepAlive()
        }
    }

    private fun startCacheCollector() {
        if (cacheJob?.isActive == true) return
        cacheJob = applicationScope.launch {
            try {
                continuousAmapLocationManager.startContinuousLocation().collect { location ->
                    locationStateManager.recordLocationSuccess(location)
                }
            } catch (_: CancellationException) {
                logI("定位缓存采集任务已取消")
            } catch (e: Exception) {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.CACHE_COLLECT_ERROR,
                    throwable = e,
                    extras = mapOf("errorMsg" to e.message)
                )
            }
        }
    }

    private fun stopCacheCollector() {
        cacheJob?.cancel()
        cacheJob = null
    }

    private fun startForegroundKeepAlive(owner: String) {
        try {
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_ACQUIRE_KEEP_ALIVE
                putExtra(LocationTrackingService.EXTRA_OWNER, owner)
            }.also {
                ContextCompat.startForegroundService(context, it)
            }
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.KEEP_ALIVE_START_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
        }
    }

    private fun stopForegroundKeepAlive() {
        try {
            Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_RELEASE_KEEP_ALIVE
            }.also {
                context.startService(it)
            }
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.KEEP_ALIVE_STOP_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
        }
    }
}
