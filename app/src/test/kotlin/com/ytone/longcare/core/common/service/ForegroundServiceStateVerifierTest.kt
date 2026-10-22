package com.ytone.longcare.core.common.service

import android.app.ActivityManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Process
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForegroundServiceStateVerifierTest {
    private val verifier = ForegroundServiceStateVerifier()
    private val locationType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @After fun teardown() = unmockkAll()

    @Test
    @Config(sdk = [24, 28])
    @Suppress("DEPRECATION")
    fun `legacy confirmation requires this service component uid pid and foreground flag`() {
        val service = mockk<Service>()
        val manager = mockk<ActivityManager>()
        every { service.packageName } returns "com.ytone.longcare"
        every { service.getSystemService(Context.ACTIVITY_SERVICE) } returns manager
        val ownComponent = ComponentName(service, service.javaClass)
        fun record(component: ComponentName = ownComponent, uid: Int = Process.myUid(),
                   pid: Int = Process.myPid(), foreground: Boolean = true) =
            ActivityManager.RunningServiceInfo().apply {
                this.service = component
                this.uid = uid
                this.pid = pid
                this.foreground = foreground
            }
        every { manager.getRunningServices(Int.MAX_VALUE) } returns emptyList()
        assertFalse(verifier.isForeground(service, locationType))
        assertFalse(verifier.isForeground(service, 0))
        every { manager.getRunningServices(Int.MAX_VALUE) } returns listOf(record(foreground = false))
        assertFalse(verifier.isForeground(service, locationType))
        assertFalse(verifier.isForeground(service, 0))
        every { manager.getRunningServices(Int.MAX_VALUE) } returns listOf(
            record(component = ComponentName("com.ytone.longcare", "OtherService")),
            record(uid = Process.myUid() + 1),
            record(pid = Process.myPid() + 1),
        )
        assertFalse(verifier.isForeground(service, locationType))
        assertFalse(verifier.isForeground(service, 0))
        every { manager.getRunningServices(Int.MAX_VALUE) } returns listOf(record())
        assertTrue(verifier.isForeground(service, locationType))
        assertTrue(verifier.isForeground(service, 0))
    }

    @Test
    @Config(sdk = [29])
    fun `modern confirmation requires actual location foreground type not another service type`() {
        val service = mockk<Service>()
        every { service.foregroundServiceType } returns ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        assertFalse(verifier.isForeground(service, locationType))
        every { service.foregroundServiceType } returns ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        assertFalse(verifier.isForeground(service, locationType))
        every { service.foregroundServiceType } returns ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        assertTrue(verifier.isForeground(service, locationType))
        every { service.foregroundServiceType } returns
            (ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue(verifier.isForeground(service, locationType))
        assertFalse(verifier.isForeground(service, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK))
        every { service.foregroundServiceType } returns ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        assertTrue(verifier.isForeground(service, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK))
        assertFalse(verifier.isForeground(service, locationType))
        assertFalse(verifier.isForeground(service, 0))
        verify(exactly = 0) { service.getSystemService(Context.ACTIVITY_SERVICE) }
    }
}
