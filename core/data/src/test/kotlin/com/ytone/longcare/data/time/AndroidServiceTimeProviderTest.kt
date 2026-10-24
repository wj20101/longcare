package com.ytone.longcare.data.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidServiceTimeProviderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `snapshot uses Android elapsed realtime and the current boot identity`() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 7)
        val elapsedBefore = SystemClock.elapsedRealtime()
        val wallBefore = System.currentTimeMillis()

        val snapshot = AndroidServiceTimeProvider(context).snapshot()

        assertEquals(7, snapshot.bootCount)
        assertTrue(snapshot.elapsedRealtimeMillis in elapsedBefore..SystemClock.elapsedRealtime())
        assertTrue(snapshot.wallTimeMillis in wallBefore..System.currentTimeMillis())
    }

    @Test
    fun `missing or invalid boot identity remains unknown`() {
        Settings.Global.putString(context.contentResolver, Settings.Global.BOOT_COUNT, null)
        assertNull(AndroidServiceTimeProvider(context).snapshot().bootCount)

        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        assertNull(AndroidServiceTimeProvider(context).snapshot().bootCount)
    }

    @Test
    fun `restricted boot identity remains unknown without preventing confirmation`() {
        val restrictedContext = mockk<Context>()
        every { restrictedContext.contentResolver } throws SecurityException("restricted")

        val provider = AndroidServiceTimeProvider(restrictedContext)
        val snapshot = provider.snapshot()

        assertNull(snapshot.bootCount)
        assertTrue(snapshot.wallTimeMillis > 0L)
        assertTrue(snapshot.elapsedRealtimeMillis >= 0L)
        assertNull(provider.snapshot().bootCount)
        verify(exactly = 1) { restrictedContext.contentResolver }
    }

    @Test
    fun `boot identity stays cached for this process`() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 7)
        val provider = AndroidServiceTimeProvider(context)
        assertEquals(7, provider.snapshot().bootCount)

        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 8)
        assertEquals(7, provider.snapshot().bootCount)
    }
}
