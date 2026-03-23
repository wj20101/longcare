package com.ytone.longcare.features.countdown.manager

internal enum class CountdownAlarmLaunchSource {
    FULL_SCREEN_NOTIFICATION,
    DIRECT_SERVICE_LAUNCH,
}

internal object CountdownAlarmPresentationPolicy {
    fun autoCloseEnabled(launchSource: CountdownAlarmLaunchSource): Boolean {
        return when (launchSource) {
            CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION -> false
            CountdownAlarmLaunchSource.DIRECT_SERVICE_LAUNCH -> false
        }
    }
}
