package com.ytone.longcare.platform.webview

import android.app.Application
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NativeBridgeTest {
    @Test fun detailsAndCloseShareOneNavigationOnMainThread() {
        val opened = mutableListOf<Int>()
        var closes = 0
        val bridge = NativeBridge({ true }, { closes++ }, {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            opened += it
        })
        bridge.enterUserDetails("7")
        bridge.enterUserDetails("8")
        bridge.closeWebView()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(7), opened)
        assertEquals(0, closes)
    }

    @Test fun closeBeforeDetailsDoesNotOpenCustomer() {
        var closes = 0
        val bridge = NativeBridge({ true }, { closes++ }, { fail("Already closed") })
        bridge.closeWebView()
        bridge.enterUserDetails("7")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun invalidArgumentsDoNotConsumeNavigation() {
        val opened = mutableListOf<Int>()
        val bridge = NativeBridge({ true }, { fail("Not a close request") }, { opened.add(it) })
        listOf(null, "", "null", "undefined", "true", "abc", "0", "-1", "1.5", "2147483648").forEach {
            bridge.enterUserDetails(it)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(opened.isEmpty())
        bridge.enterUserDetails("2147483647")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(Int.MAX_VALUE), opened)
    }

    @Test fun ordinaryContainerIgnoresDetailsAndCanStillClose() {
        var closes = 0
        val bridge = NativeBridge({ true }, { closes++ })
        bridge.enterUserDetails("7")
        bridge.closeWebView()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun inactiveContainerCanNavigateAfterResuming() {
        var active = false
        val opened = mutableListOf<Int>()
        val bridge = NativeBridge({ active }, {}, { opened.add(it) })
        bridge.enterUserDetails("7")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(opened.isEmpty())
        active = true
        bridge.enterUserDetails("8")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(8), opened)
    }

    @Test fun disposeDropsQueuedAndLateCalls() {
        val bridge = NativeBridge({ true }, { fail("Disposed") }, { fail("Disposed") })
        bridge.enterUserDetails("7")
        bridge.dispose()
        bridge.enterUserDetails("8")
        bridge.closeWebView()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
