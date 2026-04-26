package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.ytone.longcare.BuildConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultHomeLoginLogInfoProviderTest {

    @Test
    fun `build returns wifi payload with readable device and app version`() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>()
        val telephonyManager = mockk<TelephonyManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { telephonyManager.networkOperatorName } returns "Carrier"

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertEquals(formatPhoneSystem(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()), result.phoneSystem)
        assertEquals(formatPhoneVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), result.phoneVersion)
        assertEquals("WIFI", result.networkType)
        assertEquals("Carrier", result.networkOperator)
    }

    @Test
    fun `build falls back gracefully when services unavailable`() {
        val context = mockk<Context>(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns null

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertEquals(formatPhoneSystem(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()), result.phoneSystem)
        assertEquals(formatPhoneVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), result.phoneVersion)
        assertEquals("NONE", result.networkType)
        assertEquals("", result.networkOperator)
    }

    @Test
    fun `formatPhoneSystem joins manufacturer and model`() {
        val result = formatPhoneSystem(
            manufacturer = "HUAWEI",
            model = "NOH-AN00",
        )

        assertEquals("HUAWEI NOH-AN00", result)
    }

    @Test
    fun `formatPhoneSystem trims blank manufacturer or model parts`() {
        assertEquals("NOH-AN00", formatPhoneSystem(manufacturer = " ", model = "NOH-AN00"))
        assertEquals("HUAWEI", formatPhoneSystem(manufacturer = "HUAWEI", model = " "))
        assertEquals("", formatPhoneSystem(manufacturer = " ", model = " "))
    }

    @Test
    fun `formatPhoneVersion joins version name and version code`() {
        val result = formatPhoneVersion(versionName = "1.0.6", versionCode = 29)

        assertEquals("1.0.6.29", result)
    }
}
