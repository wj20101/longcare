package com.ytone.longcare.common.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class LogExtTest {

    @Before
    fun setUp() {
        ShadowLog.clear()
        KLogger.init(
            LogConfig(
                enabled = true,
                globalTag = "LogExtTest",
                currentLevel = LogLevel.VERBOSE,
                stackTraceDepth = 0,
                logToFileEnabled = false,
                maskSensitiveInfo = true
            )
        )
    }

    @Test
    fun log_masksSensitiveContent_whenMaskingEnabled() {
        val rawMessage = "token=abc123 password:456789 phone=13800138000 email=test.user@example.com id=11010519491231002X"

        KLogger.i("MaskingEnabled", rawMessage)

        val loggedMessage = ShadowLog.getLogs().last().msg

        assertTrue(loggedMessage.contains("token=***"))
        assertTrue(loggedMessage.contains("password:***"))
        assertTrue(loggedMessage.contains("138****8000"))
        assertTrue(loggedMessage.contains("t***r@example.com"))
        assertTrue(loggedMessage.contains("110105********002X"))

        assertFalse(loggedMessage.contains("abc123"))
        assertFalse(loggedMessage.contains("456789"))
        assertFalse(loggedMessage.contains("13800138000"))
        assertFalse(loggedMessage.contains("test.user@example.com"))
        assertFalse(loggedMessage.contains("11010519491231002X"))
    }

    @Test
    fun log_keepsSensitiveContent_whenMaskingDisabled() {
        KLogger.updateConfig { maskSensitiveInfo = false }
        val rawMessage = "token=abc123 phone=13800138000 email=test.user@example.com"

        KLogger.i("MaskingDisabled", rawMessage)

        val loggedMessage = ShadowLog.getLogs().last().msg

        assertTrue(loggedMessage.contains("token=abc123"))
        assertTrue(loggedMessage.contains("13800138000"))
        assertTrue(loggedMessage.contains("test.user@example.com"))
    }
}
