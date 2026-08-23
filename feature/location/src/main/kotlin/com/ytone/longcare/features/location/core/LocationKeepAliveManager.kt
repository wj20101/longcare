package com.ytone.longcare.features.location.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 定位保活生命周期管理器（独立于具体业务）。
 * 使用 owner 引用计数，避免多个业务互相抢占定位保活。
 */
@Singleton
class LocationKeepAliveManager @Inject constructor(
    private val serviceController: LocationForegroundServiceController,
) {
    private val lock = Any()
    private val activeOwners = linkedSetOf<String>()
    private var generation = 0L
    private val _state = MutableStateFlow<LocationKeepAliveState>(LocationKeepAliveState.Idle)
    internal val state: StateFlow<LocationKeepAliveState> = _state.asStateFlow()

    fun acquire(owner: String) {
        if (owner.isBlank()) return

        synchronized(lock) {
            val ownerAdded = activeOwners.add(owner)
            val explicitRestart =
                !ownerAdded && _state.value is LocationKeepAliveState.NeedsUserRestart
            if (ownerAdded || explicitRestart) {
                logI("定位保活 +1: $owner, active=${activeOwners.size}")
                val shouldStart = explicitRestart || activeOwners.size == 1
                if (shouldStart) {
                    generation += 1
                    val startGeneration = generation
                    _state.value = LocationKeepAliveState.Starting(generation, activeOwners.size)
                    val started = startForegroundKeepAlive(owner, startGeneration)
                    if (!started) {
                        activeOwners.remove(owner)
                        if (generation == startGeneration) {
                            _state.value = if (activeOwners.isEmpty()) {
                                LocationKeepAliveState.Idle
                            } else {
                                LocationKeepAliveState.NeedsUserRestart(
                                    generation = startGeneration,
                                    ownerCount = activeOwners.size,
                                )
                            }
                        }
                    }
                } else {
                    updateOwnerCountLocked()
                }
            }
        }
    }

    fun release(owner: String) {
        if (owner.isBlank()) return

        synchronized(lock) {
            if (activeOwners.remove(owner)) {
                logI("定位保活 -1: $owner, active=${activeOwners.size}")
                val shouldStop = activeOwners.isEmpty()
                _state.value = if (shouldStop) {
                    LocationKeepAliveState.Stopping(generation)
                } else {
                    updateOwnerCountLocked()
                    _state.value
                }
                if (shouldStop) {
                    stopForegroundKeepAlive()
                }
            }
        }
    }

    /**
     * Confirms that the Service generation is still desired before it starts SDK collection.
     * A stale Service must clean up itself; stopping through the controller could kill a newer one.
     */
    internal fun onServiceStarted(serviceGeneration: Long): Boolean = synchronized(lock) {
        if (serviceGeneration != generation || activeOwners.isEmpty()) {
            false
        } else {
            _state.value = LocationKeepAliveState.Active(serviceGeneration, activeOwners.size)
            true
        }
    }

    internal fun onServiceStopped(serviceGeneration: Long) {
        synchronized(lock) {
            if (serviceGeneration != generation) return
            _state.value = if (activeOwners.isEmpty()) {
                LocationKeepAliveState.Idle
            } else {
                LocationKeepAliveState.NeedsUserRestart(serviceGeneration, activeOwners.size)
            }
        }
    }

    private fun updateOwnerCountLocked() {
        _state.value = when (val current = _state.value) {
            is LocationKeepAliveState.Starting -> current.copy(ownerCount = activeOwners.size)
            is LocationKeepAliveState.Active -> current.copy(ownerCount = activeOwners.size)
            is LocationKeepAliveState.NeedsUserRestart -> current.copy(ownerCount = activeOwners.size)
            else -> current
        }
    }

    private fun startForegroundKeepAlive(owner: String, serviceGeneration: Long): Boolean {
        return try {
            serviceController.start(owner, serviceGeneration)
            true
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.KEEP_ALIVE_START_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
            false
        }
    }

    private fun stopForegroundKeepAlive() {
        try {
            val stopped = serviceController.stop()
            if (!stopped) {
                onServiceStopped(generation)
            }
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.KEEP_ALIVE_STOP_ERROR,
                throwable = e,
                extras = mapOf(LocationEventTracker.Attribute.ERROR_TYPE to e.javaClass.simpleName)
            )
        }
    }
}

@Singleton
class LocationForegroundServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun start(owner: String, generation: Long) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_ACQUIRE_KEEP_ALIVE
            putExtra(LocationTrackingService.EXTRA_OWNER, owner)
            putExtra(LocationTrackingService.EXTRA_GENERATION, generation)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(): Boolean = context.stopService(Intent(context, LocationTrackingService::class.java))
}

sealed interface LocationKeepAliveState {
    data object Idle : LocationKeepAliveState
    data class Starting(val generation: Long, val ownerCount: Int) : LocationKeepAliveState
    data class Active(val generation: Long, val ownerCount: Int) : LocationKeepAliveState
    data class Stopping(val generation: Long) : LocationKeepAliveState
    data class NeedsUserRestart(val generation: Long, val ownerCount: Int) : LocationKeepAliveState
}
