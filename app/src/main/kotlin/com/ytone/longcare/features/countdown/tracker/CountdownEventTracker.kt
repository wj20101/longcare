package com.ytone.longcare.features.countdown.tracker

import android.content.Intent
import android.content.Context
import android.os.PowerManager
import android.os.Bundle
import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object CountdownEventTracker {
    enum class EventType(val code: String, val description: String) {
        ALARM_SCHEDULE_SUCCESS("alarm_schedule_success", "闹钟设置成功"),
        ALARM_SCHEDULE_FAILED("alarm_schedule_failed", "闹钟设置失败"),
        ALARM_TRIGGERED("alarm_triggered", "闹钟触发"),
        ALARM_DELIVERY_RESULT("alarm_delivery_result", "闹钟交付时序汇总"),
        RINGTONE_SERVICE_START("ringtone_service_start", "响铃服务启动"),
        RINGTONE_SERVICE_STOP("ringtone_service_stop", "响铃服务停止"),
        RINGTONE_SERVICE_ERROR("ringtone_service_error", "响铃服务错误"),
        NOTIFICATION_BUILD("notification_build", "通知构建"),
    }

    fun trackEvent(
        eventType: EventType,
        orderId: Long? = null,
        extras: Map<String, Any?> = emptyMap(),
        reportToServer: Boolean = false,
        userId: String = DiagnosticEventTracker.currentUserId(),
    ) {
        DiagnosticEventTracker.trackEvent(
            category = DiagnosticCategory.COUNTDOWN,
            event = eventType.code,
            description = eventType.description,
            extras = extras + mapOf("orderId" to orderId),
            reportToServer = reportToServer,
            userId = userId,
        )
    }

    fun trackError(
        eventType: EventType,
        orderId: Long? = null,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
        userId: String = DiagnosticEventTracker.currentUserId(),
    ) {
        DiagnosticEventTracker.trackError(
            category = DiagnosticCategory.COUNTDOWN,
            event = eventType.code,
            description = eventType.description,
            throwable = throwable,
            extras = extras + mapOf("orderId" to orderId),
            userId = userId,
        )
    }
}

/** Diagnostic-only clocks travel with the existing alarm Intent, including across process death. */
internal object CountdownAlarmDiagnostics {
    private const val EXTRA = "extra_countdown_diagnostics"
    private const val USER = "user"
    private const val SCHEDULE_WALL = "scheduleWall"
    private const val SCHEDULE_ELAPSED = "scheduleElapsed"
    private const val SCHEDULE_UPTIME = "scheduleUptime"
    private const val RECEIVER_WALL = "receiverWall"
    private const val RECEIVER_ELAPSED = "receiverElapsed"
    private const val RECEIVER_UPTIME = "receiverUptime"
    private const val VALIDATED_ELAPSED = "validatedElapsed"
    private const val SERVICE_ELAPSED = "serviceElapsed"
    private const val MODE = "mode"
    private const val FALLBACK = "fallback"
    private const val VALIDATION_FAILURE = "validationFailure"

    fun scheduled(intent: Intent, mode: String, fallback: String?, canScheduleExact: Boolean) {
        intent.putExtra(EXTRA, Bundle().apply {
            putString(USER, DiagnosticEventTracker.currentUserId())
            putBoolean("canScheduleExact", canScheduleExact)
            putString(MODE, mode)
            putString(FALLBACK, fallback)
            putLong(SCHEDULE_WALL, System.currentTimeMillis())
            putLong(SCHEDULE_ELAPSED, SystemClock.elapsedRealtime())
            putLong(SCHEDULE_UPTIME, SystemClock.uptimeMillis())
        })
    }

    fun fallback(intent: Intent, mode: String, reason: String) {
        read(intent)?.apply { putString(MODE, mode); putString(FALLBACK, reason) }
    }

    fun receiverEntered(intent: Intent, context: Context) {
        val data = read(intent) ?: Bundle().also { intent.putExtra(EXTRA, it) }
        if (data.containsKey(RECEIVER_ELAPSED)) return
        // Capture before launching the validation coroutine, not after its suspension points.
        data.putLong(RECEIVER_WALL, System.currentTimeMillis())
        data.putLong(RECEIVER_ELAPSED, SystemClock.elapsedRealtime())
        data.putLong(RECEIVER_UPTIME, SystemClock.uptimeMillis())
        runCatching { context.getSystemService(PowerManager::class.java)?.isDeviceIdleMode }
            .getOrNull()?.let { data.putBoolean("deviceIdle", it) }
        runCatching { context.getSystemService(PowerManager::class.java)?.isInteractive }
            .getOrNull()?.let { data.putBoolean("interactive", it) }
    }

    fun validationFailed(intent: Intent, reason: String): Boolean {
        read(intent)?.putString(VALIDATION_FAILURE, reason)
        return false
    }

    fun validated(intent: Intent) {
        read(intent)?.putLong(VALIDATED_ELAPSED, SystemClock.elapsedRealtime())
    }

    fun serviceEntered(data: Bundle?) {
        data?.putLong(SERVICE_ELAPSED, SystemClock.elapsedRealtime())
    }

    fun read(intent: Intent?): Bundle? = intent?.getBundleExtra(EXTRA)
    fun attach(intent: Intent, data: Bundle?) { if (data != null) intent.putExtra(EXTRA, data) }
    fun userId(data: Bundle?, owner: String?): String =
        data?.getString(USER) ?: owner?.substringAfterLast(':') ?: "0"

    /** Missing legacy evidence stays unknown; elapsed/uptime durations never use the wall clock. */
    fun fields(data: Bundle?, plannedAt: Long, outcome: String): Map<String, Any?> {
        fun value(key: String): Long? = data?.takeIf { it.containsKey(key) }?.getLong(key)
        fun interval(end: Long?, start: Long?): Long? =
            if (end != null && start != null && end >= start) end - start else null
        val receiverWall = value(RECEIVER_WALL)
        // Old alarms can synthesize their identity timestamp on migration; do not call it a deadline.
        val knownPlannedAt = plannedAt.takeIf { it > 0L && value(SCHEDULE_WALL) != null }
        val elapsed = interval(value(RECEIVER_ELAPSED), value(SCHEDULE_ELAPSED))
        val awake = interval(value(RECEIVER_UPTIME), value(SCHEDULE_UPTIME))
        val wall = receiverWall?.let { end -> value(SCHEDULE_WALL)?.let { end - it } }
        return linkedMapOf(
            "outcome" to outcome,
            "plannedAt" to knownPlannedAt,
            "scheduleMode" to data?.getString(MODE),
            "fallback" to data?.getString(FALLBACK),
            "validationFailure" to data?.getString(VALIDATION_FAILURE),
            "canScheduleExact" to data?.takeIf { it.containsKey("canScheduleExact") }?.getBoolean("canScheduleExact"),
            "receiverDelayMs" to receiverWall?.let { end -> knownPlannedAt?.let { end - it } },
            "validationMs" to interval(value(VALIDATED_ELAPSED), value(RECEIVER_ELAPSED)),
            "serviceDispatchMs" to interval(value(SERVICE_ELAPSED), value(VALIDATED_ELAPSED)),
            "serviceSetupMs" to interval(SystemClock.elapsedRealtime(), value(SERVICE_ELAPSED)),
            "clockShiftMs" to wall?.let { elapsed?.let { delta -> it - delta } },
            "sleepMs" to elapsed?.let { duration -> awake?.takeIf { it <= duration }?.let { duration - it } },
            "scheduledAt" to value(SCHEDULE_WALL),
            "receivedAt" to receiverWall,
            "deviceIdle" to data?.takeIf { it.containsKey("deviceIdle") }?.getBoolean("deviceIdle"),
            "interactive" to data?.takeIf { it.containsKey("interactive") }?.getBoolean("interactive"),
        )
    }
}
