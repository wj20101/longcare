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
        val freshOption = source.section(
            startMarker = "private fun buildFreshLocationOption",
            endMarker = "private fun buildQuickLocationOption",
        )
        val isolatedMethod = source.section(
            startMarker = "private suspend fun getIsolatedLocation",
            endMarker = "/**\n     * 权限授予后重启定位引擎",
        )
        val currentMethod = source.section(
            startMarker = "suspend fun getCurrentLocation",
            endMarker = "suspend fun getFreshLocation",
        )

        assertTrue(isolatedMethod.contains("AMapLocationClient(context)"))
        assertTrue(isolatedMethod.contains("suspendCancellableCoroutine"))
        assertTrue(freshOption.contains("AMapLocationClientOption.AMapLocationPurpose.SignIn"))
        assertTrue(freshOption.contains("isOnceLocation = true"))
        assertTrue(freshOption.contains("isOnceLocationLatest = true"))
        assertTrue(freshOption.contains("isLocationCacheEnable = false"))
        assertTrue(currentMethod.contains("getIsolatedLocation("))
        assertFalse(currentMethod.contains("startContinuousLocation()"))
        assertFalse(currentMethod.contains(".first()"))
    }

    @Test
    fun `amap location results carry sdk diagnostic metadata`() {
        val continuousMapping = source.section(
            startMarker = "val result = LocationResult(",
            endMarker = "trySend(result)"
        )
        val isolatedMapping = source.section(
            startMarker = "val isolatedClient = AMapLocationClient(context)",
            endMarker = "isolatedClient.setLocationListener(listener)",
        )

        assertCarriesSdkMetadata(continuousMapping)
        assertCarriesSdkMetadata(isolatedMapping)
    }

    @Test
    fun `transient sdk errors do not terminate the active order location stream`() {
        val continuousListener = source.section(
            startMarker = "val listener = AMapLocationListener",
            endMarker = "client.setLocationListener(listener)",
        )

        assertTrue(continuousListener.contains("AMAP_CONTINUOUS_LOCATION_ERROR"))
        assertFalse(continuousListener.contains("close("))
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
