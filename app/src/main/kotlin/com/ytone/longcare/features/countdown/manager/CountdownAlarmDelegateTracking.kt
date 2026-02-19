package com.ytone.longcare.features.countdown.manager

import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker

internal fun trackAlarmScheduleSuccess(
    orderId: Long,
    serviceName: String,
    triggerTimeMillis: Long,
    scheduleMetadata: CountdownScheduleMetadata
) {
    CountdownEventTracker.trackEvent(
        eventType = CountdownEventTracker.EventType.ALARM_SCHEDULE_SUCCESS,
        orderId = orderId,
        extras = mapOf(
            "serviceName" to serviceName,
            "triggerTime" to triggerTimeMillis,
            "useAlarmClock" to scheduleMetadata.useAlarmClock,
            "nextAlarmTime" to scheduleMetadata.nextAlarmTime
        )
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
