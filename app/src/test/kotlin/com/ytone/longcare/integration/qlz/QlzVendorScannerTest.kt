package com.ytone.longcare.integration.qlz

import android.app.Application
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class QlzVendorScannerTest {
    @Test
    fun `real vendor timeout still reaches screen after garbage collection`() {
        val events = mutableListOf<String>()
        val scanner = scanner(events)
        try {
            scanner.startScan(30_000L)
            collectGarbage()
            // Execute the AAR's real timeout runnable without using a Bluetooth radio.
            vendorTimeout(scanner).run()
            assertEquals(listOf("start", "stop"), events)
        } finally {
            scanner.close()
        }
    }

    @Test
    fun `real vendor device callback and retry survive garbage collection`() {
        val events = mutableListOf<String>()
        val scanner = scanner(events)
        try {
            scanner.startScan(30_000L)
            collectGarbage()
            assertNotNull(scanner.bluetoothLeScan.d.get())
            scanner.bluetoothLeScan.d.get()!!.onRes(arrayListOf())
            scanner.stopScan()
            scanner.startScan(30_000L)
            collectGarbage()
            vendorTimeout(scanner).run()
            assertEquals(listOf("start", "devices", "stop", "start", "stop"), events)
        } finally {
            scanner.close()
        }
    }

    @Test
    fun `close releases vendor callback and ignores late callbacks`() {
        val events = mutableListOf<String>()
        val scanner = scanner(events)
        scanner.startScan(30_000L)
        val lateCallback = scanner.bluetoothLeScan.d.get()!!
        scanner.close()
        scanner.close()
        lateCallback.onStart()
        lateCallback.onRes(arrayListOf())
        lateCallback.onStop()
        assertEquals(listOf("start"), events)
        assertNull(scanner.bluetoothLeScan.d.get())
        assertThrows(IllegalStateException::class.java) { scanner.startScan(30_000L) }
    }

    private fun scanner(events: MutableList<String>) =
        QlzVendorScanner(
            RuntimeEnvironment.getApplication(),
            onStarted = { events += "start" },
            onStopped = { events += "stop" },
            onDevices = { events += "devices" },
        )

    // This obfuscated nested class has incomplete Kotlin-visible metadata in the AAR.
    private fun vendorTimeout(scanner: QlzVendorScanner): Runnable =
        scanner.bluetoothLeScan.javaClass.getField("f").get(scanner.bluetoothLeScan) as Runnable

    private fun collectGarbage() {
        val sentinel = WeakReference(Any())
        repeat(20) {
            System.gc()
            System.runFinalization()
            if (sentinel.get() == null) return
            Thread.sleep(10)
        }
        assertNull("GC did not run; callback retention was not exercised", sentinel.get())
    }
}
