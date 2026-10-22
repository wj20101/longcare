package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcWorkflowEffectsTest {
    @After fun teardown() = unmockkAll()

    @Test fun `only a changing configuration preserves workflow on disposal`() {
        val activity = mockk<Activity>()
        assertFalse(shouldPreserveNfcWorkflow(null))
        every { activity.isChangingConfigurations } returns false
        assertFalse(shouldPreserveNfcWorkflow(activity))
        every { activity.isChangingConfigurations } returns true
        assertTrue(shouldPreserveNfcWorkflow(activity))
    }
}
