package com.ytone.longcare.features.location.core

import android.content.Context
import android.app.ActivityManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.pm.PackageInfoCompat
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AndroidLocationRuntimeReadinessTest {
    private val context = mockk<Context>()
    private val connectivity = mockk<ConnectivityManager>()
    private val power = mockk<PowerManager>()
    private val activity = mockk<ActivityManager>()
    private val packages = mockk<PackageManager>()
    private val packageInfo = mockk<PackageInfo>()
    private val network = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()
    private val readiness = AndroidLocationRuntimeReadiness(context)

    @Before fun setUp() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.getSystemService(Context.POWER_SERVICE) } returns power
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activity
        every { context.packageManager } returns packages
        every { context.packageName } returns "com.ytone.longcare"
        every { packages.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        mockkStatic(PackageInfoCompat::class)
        every { PackageInfoCompat.getLongVersionCode(packageInfo) } returns 72L
        every { connectivity.activeNetwork } returns null
        every { power.isDeviceIdleMode } returns false
        every { power.isInteractive } returns true
        every { power.isPowerSaveMode } returns false
        every { power.isIgnoringBatteryOptimizations(any()) } returns false
        every { power.isLowPowerStandbyEnabled } returns false
        every { activity.isBackgroundRestricted } returns false
        every { connectivity.restrictBackgroundStatus } returns ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        every { connectivity.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasTransport(any()) } returns false
    }

    @After fun tearDown() { unmockkAll() }

    @Test fun `visible activity differs from location foreground service and unknown state`() {
        mockkStatic(ActivityManager::class)
        var importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        every { ActivityManager.getMyMemoryState(any()) } answers {
            firstArg<ActivityManager.RunningAppProcessInfo>().importance = importance
        }
        assertEquals(true, readiness.isAppVisible())
        importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        assertEquals(true, readiness.isAppVisible())
        importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
        assertEquals(false, readiness.isAppVisible())
        importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        assertEquals(false, readiness.isAppVisible())
        every { ActivityManager.getMyMemoryState(any()) } throws SecurityException("restricted")
        assertNull(readiness.isAppVisible())
    }

    @Test fun `no active network is distinct from an unreadable network state`() {
        val absent = readiness.diagnosticSnapshot()
        assertEquals(false, absent["networkAvailable"])
        assertEquals(false, absent["networkValidated"])
        assertEquals("NONE", absent["networkTransport"])

        every { connectivity.activeNetwork } throws SecurityException("denied")
        val unknown = readiness.diagnosticSnapshot()
        assertNull(unknown["networkAvailable"])
        assertNull(unknown["networkValidated"])
        assertNull(unknown["networkTransport"])
        assertEquals(true, unknown["screenOn"])
        assertEquals(72L, unknown["appVersionCode"])
    }

    @Test fun `validated wifi and cellular are finite transport values`() {
        every { connectivity.activeNetwork } returns network
        for ((transport, expected) in listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "WIFI",
            NetworkCapabilities.TRANSPORT_CELLULAR to "CELLULAR",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ETHERNET",
        )) {
            every { capabilities.hasTransport(any()) } answers { firstArg<Int>() == transport }
            val snapshot = readiness.diagnosticSnapshot()
            assertEquals(true, snapshot["networkAvailable"])
            assertEquals(true, snapshot["networkValidated"])
            assertEquals(expected, snapshot["networkTransport"])
        }
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        val restricted = readiness.diagnosticSnapshot()
        assertEquals(true, restricted["networkAvailable"])
        assertEquals(false, restricted["networkValidated"])
        assertEquals("OTHER", restricted["networkTransport"])
    }

    @Test fun `doze screen and power saving are separate read only facts`() {
        every { power.isDeviceIdleMode } returns true
        every { power.isInteractive } returns false
        every { power.isPowerSaveMode } returns true
        every { power.isIgnoringBatteryOptimizations("com.ytone.longcare") } returns true
        val snapshot = readiness.diagnosticSnapshot()
        assertEquals(Build.VERSION.SDK_INT, snapshot["androidApi"])
        assertEquals(72L, snapshot["appVersionCode"])
        assertEquals(true, snapshot["deviceIdle"])
        assertEquals(false, snapshot["screenOn"])
        assertEquals(true, snapshot["powerSave"])
        assertEquals(true, snapshot["batteryExempt"])
        // Confirm every service call is an explicitly listed getter; no network callback or mutation.
        verify(exactly = 1) { connectivity.activeNetwork }
        verify(exactly = 1) { power.isDeviceIdleMode }
        verify(exactly = 1) { power.isInteractive }
        verify(exactly = 1) { power.isPowerSaveMode }
        verify(exactly = 1) { power.isIgnoringBatteryOptimizations("com.ytone.longcare") }
        verify(exactly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) 1 else 0) { connectivity.restrictBackgroundStatus }
        verify(exactly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 1 else 0) { power.isLowPowerStandbyEnabled }
        confirmVerified(connectivity, power)
    }

    @Test fun `individual getter failures preserve remaining diagnostic fields`() {
        every { connectivity.activeNetwork } returns network
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } throws SecurityException()
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { power.isDeviceIdleMode } throws IllegalStateException()
        every { packages.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        val snapshot = readiness.diagnosticSnapshot()
        assertEquals(13, snapshot.size)
        assertNull(snapshot["appVersionCode"])
        assertNull(snapshot["deviceIdle"])
        assertNull(snapshot["networkValidated"])
        assertEquals(true, snapshot["networkAvailable"])
        assertEquals("WIFI", snapshot["networkTransport"])
        assertEquals(true, snapshot["screenOn"])
        assertEquals(false, snapshot["powerSave"])
        assertEquals(false, snapshot["batteryExempt"])
    }

    @Test fun `background policies remain separate from network validation and unreadable policy stays unknown`() {
        every { connectivity.activeNetwork } returns network
        every { activity.isBackgroundRestricted } returns true
        every { power.isLowPowerStandbyEnabled } returns true
        every { connectivity.restrictBackgroundStatus } returns ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val snapshot = readiness.diagnosticSnapshot()
        assertEquals(true, snapshot["networkValidated"])
        assertEquals(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) true else null, snapshot["backgroundRestricted"])
        assertEquals(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED else null, snapshot["restrictBackgroundStatus"])
        assertEquals(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) true else null,
            snapshot["lowPowerStandbyEnabled"])

        every { activity.isBackgroundRestricted } throws SecurityException()
        every { power.isLowPowerStandbyEnabled } throws IllegalStateException()
        every { connectivity.restrictBackgroundStatus } returns -1
        val unknown = readiness.diagnosticSnapshot()
        for (key in listOf("backgroundRestricted", "restrictBackgroundStatus", "lowPowerStandbyEnabled")) {
            assertNull(key, unknown[key])
        }
        assertEquals(true, unknown["networkValidated"])
        assertEquals(true, unknown["screenOn"])
    }

    @Test fun `unavailable services remain unknown without dropping app version`() {
        every { context.getSystemService(any<String>()) } returns null
        val snapshot = readiness.diagnosticSnapshot()
        assertEquals(72L, snapshot["appVersionCode"])
        for (field in listOf("networkAvailable", "networkValidated", "networkTransport",
            "deviceIdle", "deviceLightIdle", "screenOn", "powerSave", "batteryExempt",
            "backgroundRestricted", "restrictBackgroundStatus", "lowPowerStandbyEnabled")) {
            assertNull(field, snapshot[field])
        }
        verify(exactly = 0) { connectivity.activeNetwork }
        confirmVerified(connectivity, power, activity)
    }
}
