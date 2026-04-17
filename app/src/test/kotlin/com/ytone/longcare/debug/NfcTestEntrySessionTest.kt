package com.ytone.longcare.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NfcTestEntrySessionTest {

    @Before
    fun setUp() {
        NfcTestEntrySession.resetForTest()
    }

    @Test
    fun defaults_to_disabled() {
        assertFalse(NfcTestEntrySession.isEnabled())
    }

    @Test
    fun toggle_enables_then_disables() {
        assertTrue(NfcTestEntrySession.toggle())
        assertTrue(NfcTestEntrySession.isEnabled())

        assertFalse(NfcTestEntrySession.toggle())
        assertFalse(NfcTestEntrySession.isEnabled())
    }

    @Test
    fun set_enabled_updates_state_directly() {
        NfcTestEntrySession.setEnabled(true)
        assertTrue(NfcTestEntrySession.isEnabled())
    }
}
