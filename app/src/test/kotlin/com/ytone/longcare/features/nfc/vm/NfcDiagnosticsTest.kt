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
    fun `unsafe arbitrary extras are omitted while safe metadata remains`() {
        val report = buildNfcUserVisibleErrorReport(
            message = "定位失败",
            source = "bind_location_failure",
            orderKey = OrderKey(orderId = 7L, planId = 8),
            signInMode = SignInMode.START_ORDER,
            nfcDeviceId = "TAG_998877",
            extras = mapOf(
                "stage" to "bind",
                "stageName" to "bind",
                "event" to "nfc_scan",
                "eventName" to "scan_success",
                "scanSource" to "SYSTEM_NFC",
                "orderId" to 999L,
                "nfcDeviceIdLength" to 999,
                "nfcDeviceIdHash" to 123456,
                "projectCount" to 3,
                "beginImageCount" to 1,
                "centerImageCount" to 2,
                "endImageCount" to 3,
                "endType" to 1,
                "hasLongitude" to true,
                "hasLatitude" to false,
                "longitude" to "121.4737",
                "latitude" to "31.2304",
                "nfcId" to "RAW_TAG_001",
                "identityNumber" to "310101199001011234",
                "photoUrl" to "https://example.com/photo.jpg",
                "imageKey" to "img-key-123",
                "sessionToken" to "token-abc",
                "cookie" to "cookie-xyz",
                "fullUrl" to "https://example.com/report?id=1",
            )
        )

        assertEquals("bind_location_failure", report.extras["source"])
        assertEquals("定位失败", report.extras["message"])
        assertEquals("START_ORDER", report.extras["signInMode"])
        assertEquals("bind", report.extras["stage"])
        assertEquals("bind", report.extras["stageName"])
        assertEquals("nfc_scan", report.extras["event"])
        assertEquals("scan_success", report.extras["eventName"])
        assertEquals("SYSTEM_NFC", report.extras["scanSource"])
        assertEquals(7L, report.extras["orderId"])
        assertEquals("TAG_998877".length, report.extras["nfcDeviceIdLength"])
        assertEquals("TAG_998877".hashCode(), report.extras["nfcDeviceIdHash"])
        assertEquals(3, report.extras["projectCount"])
        assertEquals(1, report.extras["beginImageCount"])
        assertEquals(2, report.extras["centerImageCount"])
        assertEquals(3, report.extras["endImageCount"])
        assertEquals(1, report.extras["endType"])
        assertEquals(true, report.extras["hasLongitude"])
        assertEquals(false, report.extras["hasLatitude"])
        assertEquals(8, report.extras["planId"])

        assertFalse(report.extras.containsKey("longitude"))
        assertFalse(report.extras.containsKey("latitude"))
        assertFalse(report.extras.containsKey("nfcId"))
        assertFalse(report.extras.containsKey("identityNumber"))
        assertFalse(report.extras.containsKey("photoUrl"))
        assertFalse(report.extras.containsKey("imageKey"))
        assertFalse(report.extras.containsKey("sessionToken"))
        assertFalse(report.extras.containsKey("cookie"))
        assertFalse(report.extras.containsKey("fullUrl"))
        assertFalse(report.extras.containsValue("RAW_TAG_001"))
        assertFalse(report.extras.containsValue("310101199001011234"))
        assertFalse(report.extras.containsValue("https://example.com/photo.jpg"))
        assertFalse(report.extras.containsValue("https://example.com/report?id=1"))
    }

    @Test
    fun `pattern looking keys do not bypass the allowlist`() {
        val report = buildNfcUserVisibleErrorReport(
            message = "定位失败",
            source = "bind_location_failure",
            extras = mapOf(
                "identityType" to "id-card",
                "tokenType" to "refresh",
                "customCount" to "42",
                "hasIdentityNumber" to "310101199001011234",
            )
        )

        assertEquals("bind_location_failure", report.extras["source"])
        assertEquals("定位失败", report.extras["message"])
        assertFalse(report.extras.containsKey("identityType"))
        assertFalse(report.extras.containsKey("tokenType"))
        assertFalse(report.extras.containsKey("customCount"))
        assertFalse(report.extras.containsKey("hasIdentityNumber"))
        assertFalse(report.extras.containsValue("id-card"))
        assertFalse(report.extras.containsValue("refresh"))
        assertFalse(report.extras.containsValue("42"))
        assertFalse(report.extras.containsValue("310101199001011234"))
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
