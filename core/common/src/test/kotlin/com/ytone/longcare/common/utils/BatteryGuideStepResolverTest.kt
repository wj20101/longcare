package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryGuideStepResolverTest {

    @Test
    fun returnsRequestIgnoreOptimizations_whenNotWhitelistedAndRequestNotAttempted() {
        val step = resolveBatteryGuideStep(
            isIgnoringBatteryOptimizations = false,
            ignoreBatteryRequestAttempted = false,
            needsAutoStartGuide = true,
            autoStartGuideShown = false,
        )

        assertEquals(BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, step)
    }

    @Test
    fun returnsBatterySettings_whenNotWhitelistedAfterRequestAttempted() {
        val step = resolveBatteryGuideStep(
            isIgnoringBatteryOptimizations = false,
            ignoreBatteryRequestAttempted = true,
            needsAutoStartGuide = true,
            autoStartGuideShown = false,
        )

        assertEquals(BatteryGuideStep.OPEN_BATTERY_SETTINGS, step)
    }

    @Test
    fun returnsAutoStartSettings_whenWhitelistedButAutoStartStillNeedsGuide() {
        val step = resolveBatteryGuideStep(
            isIgnoringBatteryOptimizations = true,
            ignoreBatteryRequestAttempted = true,
            needsAutoStartGuide = true,
            autoStartGuideShown = false,
        )

        assertEquals(BatteryGuideStep.OPEN_AUTO_START_SETTINGS, step)
    }

    @Test
    fun returnsNone_whenWhitelistAndAutoStartGuideAlreadyHandled() {
        val step = resolveBatteryGuideStep(
            isIgnoringBatteryOptimizations = true,
            ignoreBatteryRequestAttempted = true,
            needsAutoStartGuide = true,
            autoStartGuideShown = true,
        )

        assertEquals(BatteryGuideStep.NONE, step)
    }
}
