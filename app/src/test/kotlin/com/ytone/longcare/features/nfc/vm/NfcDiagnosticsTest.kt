package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDiagnosticsTest {

    @Test
    fun `new error states are unreported by default`() {
        val state = NfcSignInUiState.Error("定位失败")

        assertFalse(state.buglyReported)
    }

    @Test
    fun `reportedNfcError marks existing diagnostic paths as reported`() {
        val state = reportedNfcError("接口失败")

        assertEquals("接口失败", state.message)
        assertTrue(state.buglyReported)
    }

    @Test
    fun `user visible report includes safe context and hashes nfc id`() {
        val report = buildNfcUserVisibleErrorReport(
            message = "请开启定位服务以获取位置信息",
            source = "scan_location_error",
            orderKey = OrderKey(orderId = 1001L, planId = 2002),
            signInMode = SignInMode.END_ORDER,
            nfcDeviceId = "RAW_NFC_123456",
            extras = mapOf("hasLongitude" to false, "hasLatitude" to false)
        )

        assertEquals("nfc_user_visible_error", report.event)
        assertEquals("NFC用户可见错误", report.description)
        assertEquals(1001L, report.extras["orderId"])
        assertEquals(2002, report.extras["planId"])
        assertEquals("END_ORDER", report.extras["signInMode"])
        assertEquals("scan_location_error", report.extras["source"])
        assertEquals("请开启定位服务以获取位置信息", report.extras["message"])
        assertEquals("RAW_NFC_123456".length, report.extras["nfcDeviceIdLength"])
        assertEquals("RAW_NFC_123456".hashCode(), report.extras["nfcDeviceIdHash"])
        assertFalse(report.extras.containsValue("RAW_NFC_123456"))
    }

    @Test
    fun `reportUserVisibleNfcError invokes reporter and returns reported error`() {
        var capturedReport: NfcUserVisibleErrorReport? = null

        val state = reportUserVisibleNfcError(
            message = "定位失败",
            source = "resume_permission_scan_location_error",
            orderKey = OrderKey(orderId = 10L, planId = 20),
            signInMode = SignInMode.START_ORDER,
            reporter = { report -> capturedReport = report }
        )

        assertEquals("定位失败", state.message)
        assertTrue(state.buglyReported)
        assertEquals("nfc_user_visible_error", capturedReport?.event)
        assertEquals("resume_permission_scan_location_error", capturedReport?.extras?.get("source"))
    }
}
