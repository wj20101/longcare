package com.ytone.longcare.integration.qlz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzSdkWindowInsetsCompatTest {

    @Test
    fun `form and report activity protects the status bar edge`() {
        assertTrue(
            QlzSdkWindowInsetsCompat.shouldProtectTopInset(
                "com.evenmed.sdk.ResultViewActivity"
            )
        )
    }

    @Test
    fun `immersive detection activity keeps its own top inset policy`() {
        assertFalse(
            QlzSdkWindowInsetsCompat.shouldProtectTopInset(
                "com.comm.androidview.BaseAct"
            )
        )
    }
}
