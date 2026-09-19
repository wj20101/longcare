package com.ytone.longcare.features.sales

import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.presentation.sales.SalesPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesEvaluationFlowPolicyTest {
    @Test
    fun `matching customer fields are used as exact upload context`() {
        val state =
            SalesUiState(
                selectedCustomerId = 7,
                selectedCustomer =
                    UserLatentDetailModel(
                        id = 7,
                        liveLat = " 30.123 ",
                        liveLng = " 120.456 ",
                        liveAddress = " 客户登记地址 ",
                    ),
                currentLocation =
                    LocationResult(
                        latitude = 31.0,
                        longitude = 121.0,
                        provider = "test",
                    ),
            )

        val result = state.toQlzEvaluationUploadContext("草稿地址")

        assertEquals("30.123", result.latitude)
        assertEquals("120.456", result.longitude)
        assertEquals("客户登记地址", result.address)
    }

    @Test
    fun `reliable registration location fills absent customer fields without guessing`() {
        val state =
            SalesUiState(
                selectedCustomerId = 7,
                selectedCustomer = UserLatentDetailModel(id = 8),
                currentLocation =
                    LocationResult(
                        latitude = 30.25,
                        longitude = 120.75,
                        provider = "amap",
                    ),
            )

        val result = state.toQlzEvaluationUploadContext(" 已确认地址 ")

        assertEquals("30.25", result.latitude)
        assertEquals("120.75", result.longitude)
        assertEquals("已确认地址", result.address)
    }

    @Test
    fun `missing upload fields remain empty`() {
        val result = SalesUiState(selectedCustomerId = 7)
            .toQlzEvaluationUploadContext("")

        assertEquals("", result.latitude)
        assertEquals("", result.longitude)
        assertEquals("", result.address)
    }

    @Test
    fun `device and guide pages share one session while measurement stages open guide`() {
        assertTrue(SalesPage.DEVICE_STATUS.ownsQlzEvaluationSession())
        assertTrue(SalesPage.EVALUATION_GUIDE.ownsQlzEvaluationSession())
        assertFalse(SalesPage.EVALUATION_COMPLETE.ownsQlzEvaluationSession())
        assertFalse(QlzEvaluationStage.SCANNING.opensMeasurementPage())
        assertTrue(QlzEvaluationStage.CONNECTING.opensMeasurementPage())
        assertTrue(QlzEvaluationStage.UPLOADING.opensMeasurementPage())
        assertTrue(QlzEvaluationRecoveryAction.RETRY_SCAN.returnsToDeviceScan())
        assertFalse(QlzEvaluationRecoveryAction.RETRY_UPLOAD.returnsToDeviceScan())
    }

    @Test
    fun `token restart and recreated session return guide to actionable device page`() {
        assertTrue(QlzEvaluationStage.AUTHORIZING.opensDevicePage())
        assertTrue(QlzEvaluationStage.SCAN_RESULTS.opensDevicePage())
        assertTrue(QlzEvaluationStage.IDLE.opensDevicePage())
        assertFalse(QlzEvaluationStage.MEASURING.opensDevicePage())
        assertFalse(QlzEvaluationStage.UPLOADING.opensDevicePage())
    }
}
