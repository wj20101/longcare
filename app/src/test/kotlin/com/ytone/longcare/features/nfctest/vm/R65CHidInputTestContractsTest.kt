package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class R65CHidInputTestContractsTest {

    @Test
    fun `default state starts waiting for focus with empty displays`() {
        val state = R65CHidPanelState()

        assertEquals(R65CHidCaptureState.WaitingForFocus, state.captureState)
        assertEquals("", state.liveInputBuffer)
        assertEquals("-", state.lastRawInputDisplay)
        assertEquals("未解析出卡号", state.lastNormalizedUidDisplay)
        assertEquals("-", state.lastCompletedAtDisplay)
        assertEquals(0L, state.focusRequestToken)
    }

    @Test
    fun `completed state exposes raw normalized and completed time`() {
        val state = R65CHidPanelState(
            captureState = R65CHidCaptureState.LastCaptureSucceeded,
            lastRawInput = "ab12\r\n",
            lastNormalizedUid = "AB12",
            lastCompletedAt = "12:34:56",
            focusRequestToken = 2L,
        )

        assertEquals("ab12\r\n", state.lastRawInputDisplay)
        assertEquals("AB12", state.lastNormalizedUidDisplay)
        assertEquals("12:34:56", state.lastCompletedAtDisplay)
        assertEquals(2L, state.focusRequestToken)
    }

    @Test
    fun `failed capture exposes fallback uid display`() {
        val failureReason = "输入无效"
        val state = R65CHidPanelState(
            captureState = R65CHidCaptureState.LastCaptureFailed(failureReason),
            lastRawInput = "###",
        )

        assertEquals("###", state.lastRawInputDisplay)
        assertEquals("未解析出卡号", state.lastNormalizedUidDisplay)
        assertEquals(failureReason, (state.captureState as R65CHidCaptureState.LastCaptureFailed).reason)
    }

    @Test
    fun `blank normalized uid falls back to placeholder`() {
        val state = R65CHidPanelState(lastNormalizedUid = "")

        assertEquals("未解析出卡号", state.lastNormalizedUidDisplay)
    }
}
