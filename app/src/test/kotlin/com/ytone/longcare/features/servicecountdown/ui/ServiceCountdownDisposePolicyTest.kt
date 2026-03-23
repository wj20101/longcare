package com.ytone.longcare.features.servicecountdown.ui

import org.junit.Assert.assertFalse
import org.junit.Test

class ServiceCountdownDisposePolicyTest {

    @Test
    fun `screen dispose should not cancel countdown alarm`() {
        val disposeActions = resolveServiceCountdownDisposeActions()

        assertFalse(disposeActions.cancelCountdownAlarm)
    }

    @Test
    fun `screen dispose should not stop alarm ringtone`() {
        val disposeActions = resolveServiceCountdownDisposeActions()

        assertFalse(disposeActions.stopAlarmRingtone)
    }
}
