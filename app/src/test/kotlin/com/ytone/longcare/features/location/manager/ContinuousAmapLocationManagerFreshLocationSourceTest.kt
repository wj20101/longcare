package com.ytone.longcare.features.location.manager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAmapLocationManagerFreshLocationSourceTest {

    @Test
    fun `fresh location uses isolated amap client and disables cached replay sources`() {
        val source = File(
            "../feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt"
        ).readText()
        val method = source
            .substringAfter("suspend fun getFreshLocation")
            .substringBefore("/**\n     * 权限授予后重启定位引擎")

        assertTrue(method.contains("AMapLocationClient(context)"))
        assertTrue(method.contains("suspendCancellableCoroutine"))
        assertTrue(method.contains("AMapLocationClientOption.AMapLocationPurpose.SignIn"))
        assertTrue(method.contains("setOnceLocation(true)"))
        assertTrue(method.contains("setOnceLocationLatest(true)"))
        assertTrue(method.contains("setLocationCacheEnable(false)"))
        assertFalse(method.contains("startContinuousLocation().first()"))
        assertFalse(method.contains("getCurrentLocation(timeoutMs)"))
    }

    @Test
    fun `amap location results carry sdk diagnostic metadata`() {
        val source = File(
            "../feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt"
        ).readText()

        assertTrue(source.contains("coordType = location.coordType.orEmpty()"))
        assertTrue(source.contains("locationType = location.locationType"))
        assertTrue(source.contains("trustedLevel = location.trustedLevel"))
        assertTrue(source.contains("locationTime = location.time"))
    }
}
