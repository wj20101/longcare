package com.ytone.longcare.features.countdown.manager

import org.junit.Assert.assertFalse
import org.junit.Test

class CountdownAlarmPresentationPolicyTest {

    @Test
    fun `full screen notification launch should keep alarm activity open until user handles it`() {
        val autoCloseEnabled = CountdownAlarmPresentationPolicy.autoCloseEnabled(
            launchSource = CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION
        )

        assertFalse(autoCloseEnabled)
    }

    @Test
    fun `direct service launch should keep alarm activity open until user handles it`() {
        val autoCloseEnabled = CountdownAlarmPresentationPolicy.autoCloseEnabled(
            launchSource = CountdownAlarmLaunchSource.DIRECT_SERVICE_LAUNCH
        )

        assertFalse(autoCloseEnabled)
    }
}
