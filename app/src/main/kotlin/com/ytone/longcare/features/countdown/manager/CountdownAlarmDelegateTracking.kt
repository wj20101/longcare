package com.ytone.longcare.features.countdown.manager

import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics

internal fun trackAlarmScheduleSuccess(
    orderId: Long,
    triggerTimeMillis: Long,
    scheduleMetadata: CountdownScheduleMetadata
) {
    CountdownEventTracker.trackEvent(
        eventType = CountdownEventTracker.EventType.ALARM_SCHEDULE_SUCCESS,
        orderId = orderId,
        extras = CountdownAlarmDiagnostics.fields(scheduleMetadata.diagnostics, triggerTimeMillis, "scheduled") +
            mapOf("nextAlarmTime" to scheduleMetadata.nextAlarmTime),
        userId = CountdownAlarmDiagnostics.userId(scheduleMetadata.diagnostics, null)
    )
}

internal fun trackAlarmScheduleFailure(
    orderId: Long,
    serviceName: String,
    triggerTimeMillis: Long,
    error: Throwable
) {
    CountdownEventTracker.trackError(
        eventType = CountdownEventTracker.EventType.ALARM_SCHEDULE_FAILED,
        orderId = orderId,
        throwable = error,
        extras = mapOf(
            "serviceName" to serviceName,
            "triggerTime" to triggerTimeMillis
        )
    )
}
