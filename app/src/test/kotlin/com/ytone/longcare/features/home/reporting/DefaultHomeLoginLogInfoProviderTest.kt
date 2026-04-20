package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultHomeLoginLogInfoProviderTest {

    @Test
    fun `build returns wifi payload with operator`() {
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

        assertEquals("Android", result.phoneSystem)
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

        assertEquals("Android", result.phoneSystem)
        assertEquals("NONE", result.networkType)
        assertEquals("", result.networkOperator)
    }
}
