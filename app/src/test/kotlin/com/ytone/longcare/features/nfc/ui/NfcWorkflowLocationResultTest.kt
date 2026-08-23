package com.ytone.longcare.features.nfc.ui

import com.ytone.longcare.features.nfc.vm.LocationRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcWorkflowLocationResultTest {

    @Test
    fun `blank longitude is treated as location error`() {
        val result = toLocationRequestResult("", "31.23", "定位不可用")

        assertTrue(result is LocationRequestResult.Error)
        assertFalse((result as LocationRequestResult.Error).buglyReported)
    }

    @Test
    fun `blank latitude is treated as location error`() {
        val result = toLocationRequestResult("121.47", " ", "定位不可用")

        assertTrue(result is LocationRequestResult.Error)
        assertFalse((result as LocationRequestResult.Error).buglyReported)
    }

    @Test
    fun `non blank location is treated as coordinates`() {
        val result = toLocationRequestResult("121.47", "31.23", "定位不可用")

        assertEquals(LocationRequestResult.Coordinates("121.47", "31.23"), result)
    }
}
