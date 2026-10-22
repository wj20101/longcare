package com.ytone.longcare.features.location.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import com.ytone.longcare.features.location.service.LocationTrackingService
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One accepted order owner. Generation fences delayed Android callbacks across restarts. */
@Singleton
class LocationKeepAliveManager @Inject constructor(
    private val serviceController: LocationForegroundServiceController,
) {
    private val lock = Any()
    private var owner: String? = null
    private var generation = 0L
    // An accepted dispatch is idempotent until Android reports that its Service stopped.
    private var startAccepted = false
    internal fun hasAcceptedStart(): Boolean = synchronized(lock) { startAccepted }

    fun acquireOrderTracking(owner: String, orderId: Long): Boolean = synchronized(lock) {
        if (owner.isBlank() || orderId <= 0L) return@synchronized false
        if (this.owner == owner && startAccepted) {
            return@synchronized true
        }
        this.owner = owner
        generation += 1
        startAccepted = true
        try {
            serviceController.start(owner, generation, orderId)
            true
        } catch (error: Exception) {
            this.owner = null
            startAccepted = false
            LocationEventTracker.trackError(LocationEventTracker.EventType.KEEP_ALIVE_START_ERROR, error,
                mapOf(LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName))
            false
        }
    }

    /** The system already owns the Service; no application background start is dispatched. */
    internal fun adoptRestoredService(owner: String): Long = synchronized(lock) {
        this.owner = owner
        generation += 1
        startAccepted = true
        generation
    }

    internal fun hasNewerRequestThan(owner: String, serviceGeneration: Long): Boolean = synchronized(lock) {
        this.owner != null && (this.owner != owner || generation != serviceGeneration)
    }

    fun release(owner: String) = synchronized(lock) { releaseLocked(owner) }

    internal fun releaseFromService(owner: String, serviceGeneration: Long) = synchronized(lock) {
        if (generation == serviceGeneration) releaseLocked(owner)
    }

    internal fun onServiceStarted(serviceGeneration: Long): Boolean = synchronized(lock) {
        if (generation != serviceGeneration || owner == null) false
        else {
            startAccepted = true
            true
        }
    }

    internal fun onServiceStopped(serviceGeneration: Long) = synchronized(lock) {
        if (generation == serviceGeneration) startAccepted = false
    }

    private fun releaseLocked(owner: String) {
        if (this.owner != owner) return
        this.owner = null
        startAccepted = false
        try {
            serviceController.stop()
        } catch (error: Exception) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.KEEP_ALIVE_STOP_ERROR, error,
                mapOf(LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName))
        }
    }
}

@Singleton
class LocationForegroundServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val readiness: LocationRuntimeReadiness,
) {
    fun start(owner: String, generation: Long, orderId: Long) {
        check(isLocationProcessVisible()) {
            "location_requires_visible_activity"
        }
        check(readiness.hasLocationPermission() && readiness.isLocationServiceEnabled()) {
            "location_runtime_not_ready"
        }
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_ACQUIRE_KEEP_ALIVE
            putExtra(LocationTrackingService.EXTRA_OWNER, owner)
            putExtra(LocationTrackingService.EXTRA_GENERATION, generation)
            putExtra(LocationTrackingService.EXTRA_ORDER_ID, orderId)
        }
        // ContextCompat discards the platform's nullable dispatch result. Android can silently
        // reject a restricted launch; no Service callback will then release the accepted owner.
        val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        checkNotNull(component) { "location_foreground_dispatch_rejected" }
    }

    fun stop(): Boolean = context.stopService(Intent(context, LocationTrackingService::class.java))
}
