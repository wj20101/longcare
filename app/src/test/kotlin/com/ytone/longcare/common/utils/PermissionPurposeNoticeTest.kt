package com.ytone.longcare.common.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPurposeNoticeTest {

    @Test
    fun `camera notice names permission feature and purpose`() {
        val notice = cameraPermissionPurposeNotice("拍摄服务照片")

        assertTrue(notice.title.contains("相机权限"))
        assertTrue(notice.message.contains("拍摄服务照片"))
        assertTrue(notice.message.contains("主动使用"))
    }

    @Test
    fun `location notice names permission feature and purpose`() {
        val notice = locationPermissionPurposeNotice("记录NFC签到位置")

        assertTrue(notice.title.contains("定位权限"))
        assertTrue(notice.message.contains("记录NFC签到位置"))
        assertTrue(notice.message.contains("主动使用"))
    }
}
