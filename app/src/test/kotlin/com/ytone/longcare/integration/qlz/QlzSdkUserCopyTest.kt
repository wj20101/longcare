package com.ytone.longcare.integration.qlz

import org.junit.Assert.assertEquals
import org.junit.Test

class QlzSdkUserCopyTest {
    @Test
    fun `technical error text is replaced with user friendly copy`() {
        val technicalMessages =
            listOf(
                "SDK 初始化失败",
                "Token is empty",
                "API code=500",
                "缺少 QLZ_SDK_KEY，请检查 local.properties",
                "设备 ID 为空",
            )

        technicalMessages.forEach { message ->
            assertEquals(
                "评估暂时无法继续，请稍后重试",
                message.toUserFacingEvaluationError(),
            )
        }
    }

    @Test
    fun `actionable device guidance is preserved`() {
        assertEquals(
            "请打开蓝牙后重试",
            "请打开蓝牙后重试".toUserFacingEvaluationError(),
        )
    }
}
