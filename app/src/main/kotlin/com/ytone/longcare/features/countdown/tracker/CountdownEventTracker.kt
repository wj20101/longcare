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
        ALARM_RECEIVER_FINISHED("alarm_receiver_finished", "闹钟广播异步处理结束"),
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
            extras = mapOf("orderId" to orderId) + (extras - "orderId"),
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
            extras = mapOf("orderId" to orderId) + (extras - "orderId"),
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
    private const val PLANNED_AT = "plannedAt"
    private const val MODE = "mode"
    private const val FALLBACK = "fallback"
    private const val VALIDATION_FAILURE = "validationFailure"

    /** A sample describes an app boundary; it does not prove system dispatch or audible playback. */
    data class ClockSample(val wall: Long, val elapsed: Long, val uptime: Long) {
        companion object {
            fun capture() = ClockSample(System.currentTimeMillis(), SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
        }
    }

    private fun Bundle.record(stage: String, at: ClockSample) {
        putLong("${stage}Wall", at.wall)
        putLong("${stage}Elapsed", at.elapsed)
        putLong("${stage}Uptime", at.uptime)
    }

    fun scheduled(
        intent: Intent, mode: String, fallback: String?, canScheduleExact: Boolean,
        plannedAtMillis: Long? = null,
    ) {
        intent.putExtra(EXTRA, Bundle().apply {
            putString(USER, DiagnosticEventTracker.currentUserId())
            putBoolean("canScheduleExact", canScheduleExact)
            putString(MODE, mode)
            putString(FALLBACK, fallback)
            plannedAtMillis?.takeIf { it > 0L }?.let { putLong(PLANNED_AT, it) }
            record("schedule", ClockSample.capture())
        })
    }

    fun fallback(intent: Intent, mode: String, reason: String) {
        read(intent)?.apply { putString(MODE, mode); putString(FALLBACK, reason) }
    }

    fun receiverEntered(intent: Intent, context: Context) {
        val data = read(intent) ?: Bundle().also { intent.putExtra(EXTRA, it) }
        if (data.containsKey(RECEIVER_ELAPSED)) return
        // Capture before launching the validation coroutine, not after its suspension points.
        data.record("receiver", ClockSample.capture())
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
        read(intent)?.record("validated", ClockSample.capture())
    }

    fun validationStarted(intent: Intent, at: ClockSample = ClockSample.capture()) {
        read(intent)?.record("validationStarted", at)
    }

    /** Use a separate event: the Service may already own a parcelled copy of the Bundle. */
    fun receiverFinished(intent: Intent, at: ClockSample = ClockSample.capture()): Bundle? =
        read(intent)?.let { Bundle(it).apply { record("receiverFinish", at) } }

    fun serviceEntered(data: Bundle?, createdAt: ClockSample? = null, at: ClockSample = ClockSample.capture()) {
        data?.apply {
            createdAt?.let { record("serviceCreated", it) }
            record("service", at)
        }
    }

    fun wakeLockAttempted(data: Bundle?, heldByApp: Boolean?, failure: String? = null, at: ClockSample = ClockSample.capture()) {
        data?.apply {
            record("wakeLockAttempt", at)
            heldByApp?.let { putBoolean("wakeLockHeldByApp", it) }
            if (heldByApp == true) record("wakeLockAcquired", at)
            failure?.let { putString("wakeLockFailure", it) }
        }
    }

    fun playbackPrepared(data: Bundle?, workerAt: ClockSample?, callbackAt: ClockSample = ClockSample.capture()) {
        data?.apply {
            workerAt?.let { record("workerPrepared", it) }
            record("preparedCallback", callbackAt)
        }
    }

    /** Preserve worker progress even when its main callback arrives after cancellation/timeout. */
    fun playbackProgress(data: Bundle?, preparedAt: ClockSample?, nativeAt: ClockSample?) {
        data?.apply {
            preparedAt?.let { record("workerPrepared", it) }
            nativeAt?.let { record("nativeStarted", it) }
        }
    }

    fun playbackStarted(data: Bundle?, nativeAt: ClockSample?, callbackAt: ClockSample = ClockSample.capture()) {
        data?.apply {
            nativeAt?.let { record("nativeStarted", it) }
            record("playbackCallback", callbackAt)
        }
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
        val knownPlannedAt = value(PLANNED_AT)?.takeIf { it > 0L }
            ?: plannedAt.takeIf { it > 0L && value(SCHEDULE_WALL) != null }
        val elapsed = interval(value(RECEIVER_ELAPSED), value(SCHEDULE_ELAPSED))
        val awake = interval(value(RECEIVER_UPTIME), value(SCHEDULE_UPTIME))
        val wall = receiverWall?.let { end -> value(SCHEDULE_WALL)?.let { end - it } }
        fun duration(start: String, end: String): Long? = interval(value("${end}Elapsed"), value("${start}Elapsed"))
        fun sleep(start: String, end: String): Long? = duration(start, end)?.let { elapsedMs ->
            interval(value("${end}Uptime"), value("${start}Uptime"))?.takeIf { it <= elapsedMs }?.let { elapsedMs - it }
        }
        val fields = linkedMapOf<String, Any?>(
            "outcome" to outcome,
            "alarmId" to plannedAt.takeIf { it > 0L },
            "plannedAt" to knownPlannedAt,
            "scheduleMode" to data?.getString(MODE),
            "fallback" to data?.getString(FALLBACK),
            "validationFailure" to data?.getString(VALIDATION_FAILURE),
            "canScheduleExact" to data?.takeIf { it.containsKey("canScheduleExact") }?.getBoolean("canScheduleExact"),
            "receiverDelayMs" to receiverWall?.let { end -> knownPlannedAt?.let { end - it } },
            // Includes process startup and main-Looper queueing before app onReceive; not pure OS delivery.
            "receiverDelayBoundary" to "app_on_receive",
            "validationQueueMs" to duration("receiver", "validationStarted"),
            "validationWorkMs" to duration("validationStarted", "validated"),
            "validationMs" to interval(value(VALIDATED_ELAPSED), value(RECEIVER_ELAPSED)),
            "receiverLifetimeMs" to duration("receiver", "receiverFinish"),
            "receiverSleepMs" to sleep("receiver", "receiverFinish"),
            "serviceDispatchMs" to interval(value(SERVICE_ELAPSED), value(VALIDATED_ELAPSED)),
            "serviceDispatchSleepMs" to sleep("validated", "service"),
            "serviceSetupMs" to interval(SystemClock.elapsedRealtime(), value(SERVICE_ELAPSED)),
            "serviceToWakeLockMs" to duration("service", "wakeLockAcquired"),
            "wakeLockHeldByApp" to data?.takeIf { it.containsKey("wakeLockHeldByApp") }?.getBoolean("wakeLockHeldByApp"),
            "wakeLockFailure" to data?.getString("wakeLockFailure"),
            "preparedCallbackQueueMs" to duration("workerPrepared", "preparedCallback"),
            "preparedToNativeStartMs" to duration("workerPrepared", "nativeStarted"),
            "playbackCallbackQueueMs" to duration("nativeStarted", "playbackCallback"),
            "serviceToNativeStartMs" to duration("service", "nativeStarted"),
            "clockShiftMs" to wall?.let { elapsed?.let { delta -> it - delta } },
            "sleepMs" to elapsed?.let { duration -> awake?.takeIf { it <= duration }?.let { duration - it } },
            "scheduledAt" to value(SCHEDULE_WALL),
            "receivedAt" to receiverWall,
            "deviceIdle" to data?.takeIf { it.containsKey("deviceIdle") }?.getBoolean("deviceIdle"),
            "interactive" to data?.takeIf { it.containsKey("interactive") }?.getBoolean("interactive"),
        )
        // The shared diagnostic payload is capped at 950 characters. Each existing stage event
        // prioritizes its own clocks, rather than silently losing later stages in one huge map.
        val (stages, timingKeys) = when {
            outcome == "receiver_finish_returned" ->
                listOf("receiver", "validationStarted", "validated", "receiverFinish") to
                    listOf("validationQueueMs", "validationWorkMs", "receiverLifetimeMs", "receiverSleepMs")
            outcome == "service_entered" ->
                listOf("validated", "serviceCreated", "service", "wakeLockAttempt") to
                    listOf("serviceDispatchMs", "serviceDispatchSleepMs", "serviceToWakeLockMs", "wakeLockHeldByApp", "wakeLockFailure")
            value("workerPreparedElapsed") != null || value("nativeStartedElapsed") != null ->
                listOf("workerPrepared", "preparedCallback", "nativeStarted", "playbackCallback") to
                    listOf("preparedCallbackQueueMs", "preparedToNativeStartMs", "playbackCallbackQueueMs", "serviceToNativeStartMs")
            else -> listOf("receiver", "validationStarted", "validated") to
                listOf("validationQueueMs", "validationWorkMs", "validationMs", "serviceDispatchMs")
        }
        val evidence = linkedMapOf<String, Any?>(
            "outcome" to outcome, "alarmId" to fields["alarmId"], "plannedAt" to knownPlannedAt,
            "receiverDelayMs" to fields["receiverDelayMs"], "receiverDelayBoundary" to "app_on_receive",
            "scheduleMode" to fields["scheduleMode"],
        )
        if (value("nativeStartedElapsed") != null) evidence["nativeStartBoundary"] = "start_returned"
        val clocks = stages.mapNotNull { stage ->
            val wallAt = value("${stage}Wall") ?: return@mapNotNull null
            val elapsedAt = value("${stage}Elapsed") ?: return@mapNotNull null
            val uptimeAt = value("${stage}Uptime") ?: return@mapNotNull null
            "${stage}Clock" to "$wallAt/$elapsedAt/$uptimeAt"
        }
        if (clocks.isNotEmpty()) evidence["clockOrder"] = "wall/elapsed/uptime"
        clocks.forEach { (key, sample) -> evidence[key] = sample }
        timingKeys.forEach { key -> evidence[key] = fields[key] }
        // Keep legacy fields available to callers; nulls must not consume the 48-input-field cap.
        fields.forEach { (key, value) -> if (key !in evidence) evidence[key] = value }
        return evidence.filterValues { it != null }
    }
}
