package com.ytone.longcare.features.servicecountdown.ui

internal data class ServiceCountdownDisposeActions(
    val cancelCountdownAlarm: Boolean = false,
    val stopAlarmRingtone: Boolean = false,
)

internal fun resolveServiceCountdownDisposeActions(): ServiceCountdownDisposeActions {
    return ServiceCountdownDisposeActions()
}
