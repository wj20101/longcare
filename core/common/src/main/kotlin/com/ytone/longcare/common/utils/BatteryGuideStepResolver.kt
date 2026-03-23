package com.ytone.longcare.common.utils

internal fun resolveBatteryGuideStep(
    isIgnoringBatteryOptimizations: Boolean,
    ignoreBatteryRequestAttempted: Boolean,
    needsAutoStartGuide: Boolean,
    autoStartGuideShown: Boolean,
): BatteryGuideStep {
    if (!isIgnoringBatteryOptimizations) {
        return if (ignoreBatteryRequestAttempted) {
            BatteryGuideStep.OPEN_BATTERY_SETTINGS
        } else {
            BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        }
    }

    if (needsAutoStartGuide && !autoStartGuideShown) {
        return BatteryGuideStep.OPEN_AUTO_START_SETTINGS
    }

    return BatteryGuideStep.NONE
}
