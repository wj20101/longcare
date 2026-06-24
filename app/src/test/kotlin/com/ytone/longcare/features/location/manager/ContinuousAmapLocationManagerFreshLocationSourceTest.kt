package com.ytone.longcare.features.location.manager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAmapLocationManagerFreshLocationSourceTest {

    private val source by lazy {
        File(
            "../feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt"
        ).readText()
    }

    @Test
    fun `fresh location uses isolated amap client and disables cached replay sources`() {
        val method = source.section(
            startMarker = "suspend fun getFreshLocation",
            endMarker = "/**\n     * 权限授予后重启定位引擎"
        )

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
        val continuousMapping = source.section(
            startMarker = "val result = LocationResult(",
            endMarker = "trySend(result)"
        )
        val freshMapping = source.section(
            startMarker = "finish(\n                                    LocationResult(",
            endMarker = "\n                                )"
        )

        assertCarriesSdkMetadata(continuousMapping)
        assertCarriesSdkMetadata(freshMapping)
    }

    private fun assertCarriesSdkMetadata(section: String) {
        assertTrue(section.contains("coordType = location.coordType.orEmpty()"))
        assertTrue(section.contains("locationType = location.locationType"))
        assertTrue(section.contains("trustedLevel = location.trustedLevel"))
        assertTrue(section.contains("locationTime = location.time"))
    }

    private fun String.section(startMarker: String, endMarker: String): String {
        return substringAfter(startMarker).substringBefore(endMarker)
    }
}
