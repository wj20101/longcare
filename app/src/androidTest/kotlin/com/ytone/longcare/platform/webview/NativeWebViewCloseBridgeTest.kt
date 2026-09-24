package com.ytone.longcare.platform.webview

import android.annotation.SuppressLint
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** API 24-compatible harness, all responses supplied locally; no customer requests. */
@SuppressLint("SetJavaScriptEnabled")
@RunWith(AndroidJUnit4::class)
class NativeWebViewCloseBridgeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var view: WebView
    private lateinit var bridge: NativeBridge
    private val finished = AtomicInteger()
    private val closes = AtomicInteger()
    private var active = true
    private val origin = "https://careweb.ytone.cn"

    private fun show(html: String = "<h1>受控关闭测试</h1>", url: String = "$origin/form", onDetails: ((Int) -> Unit)? = null) {
        instrumentation.runOnMainSync {
            WebView(instrumentation.targetContext).apply {
                view = this
                settings.javaScriptEnabled = true
                bridge = NativeBridge({ active }, {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    closes.incrementAndGet()
                }, onDetails)
                addJavascriptInterface(bridge, NativeBridge.NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest): WebResourceResponse {
                        val content = if (request.url.path == "/child")
                            "<script>window.NativeBridge.closeWebView();</script>child" else html
                        return WebResourceResponse("text/html", "UTF-8", content.byteInputStream())
                    }
                    override fun onPageFinished(v: WebView?, target: String?) { finished.incrementAndGet() }
                }
                loadUrl(url)
            }
        }
        waitUntil(10_000) { finished.get() > 0 }
    }

    @After fun release() {
        instrumentation.runOnMainSync {
            if (::bridge.isInitialized) bridge.dispose()
            if (::view.isInitialized) {
                view.removeJavascriptInterface(NativeBridge.NAME)
                view.destroy()
            }
        }
    }

    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "WebView condition timed out" }
            SystemClock.sleep(10)
        }
    }

    private fun js(script: String): String {
        val result = AtomicReference<String?>()
        instrumentation.runOnMainSync { view.evaluateJavascript(script, result::set) }
        waitUntil(5_000) { result.get() != null }
        return result.get()!!
    }

    @Test fun nativeMethodIsRegisteredBeforeFirstPageScriptAndClosesOnMainThreadOnce() {
        show("<script>window.earlyBridge=typeof NativeBridge.closeWebView;</script><h1>受控评估</h1>")
        assertEquals("\"function\"", js("window.earlyBridge"))
        assertEquals("[\"closeWebView\",\"enterUserDetails\"]", js("Object.keys(window.NativeBridge).sort()"))
        assertEquals("\"undefined\"", js("typeof window.NativeBridge.getClass"))
        assertEquals("\"undefined\"", js("typeof window.__longcareLegacyClose"))
        assertEquals("\"undefined\"", js("typeof window.__longcareWebViewClose"))
        js("window.NativeBridge.closeWebView();window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun merelyLoadingThePageDoesNotCloseIt() {
        show()
        instrumentation.runOnMainSync { assertEquals(0, closes.get()) }
    }

    @Test fun numericCustomerIdIsConvertedByRealWebView() {
        val id = AtomicInteger()
        show(onDetails = { assertEquals(Looper.getMainLooper(), Looper.myLooper()); id.set(it) })
        js("window.NativeBridge.enterUserDetails(123);window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { id.get() == 123 }
        assertEquals(0, closes.get())
    }

    @Test fun invalidArgumentsLeavePageAvailableForDecimalString() {
        val id = AtomicInteger()
        show(onDetails = id::set)
        js("[undefined,null,'',0,-1,1.5,'abc',2147483648].forEach(v=>window.NativeBridge.enterUserDetails(v));true")
        instrumentation.runOnMainSync { assertEquals(0, id.get()); assertEquals(0, closes.get()) }
        js("window.NativeBridge.enterUserDetails('456');true")
        waitUntil(5_000) { id.get() == 456 }
    }

    @Test fun ordinaryPageCannotOpenDetailsAndCanStillClose() {
        show()
        js("window.NativeBridge.enterUserDetails(123);true")
        instrumentation.runOnMainSync { assertEquals(0, closes.get()) }
        js("window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun inactiveThenActiveCloseDoesNotConsumeClosePermission() {
        show()
        instrumentation.runOnMainSync { active = false }
        js("window.NativeBridge.closeWebView();true")
        instrumentation.runOnMainSync { assertEquals(0, closes.get()); active = true }
        js("window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun reloadAndSameOriginNavigationKeepNativeRegistration() {
        show()
        repeat(2) { index ->
            finished.set(0)
            instrumentation.runOnMainSync { if (index == 0) view.reload() else view.loadUrl("$origin/next") }
            waitUntil(10_000) { finished.get() > 0 }
            assertEquals("\"function\"", js("typeof window.NativeBridge.closeWebView"))
        }
        js("window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun embeddedFramesShareTheExplicitlyDocumentedPageTrustBoundary() {
        show("""<iframe src="$origin/child"></iframe><iframe src="https://foreign.invalid/child"></iframe>""")
        waitUntil(5_000) { closes.get() == 1 }
        assertEquals("2", js("window.frames.length"))
        assertEquals(1, closes.get())
    }

    @Test fun anyInitialOriginHasNativeInterface() {
        show(url = "https://foreign.invalid/page")
        assertEquals("\"function\"", js("typeof window.NativeBridge.closeWebView"))
        assertEquals(0, closes.get())
    }

    @Test fun crossOriginNavigationKeepsNativeRegistration() {
        show()
        finished.set(0)
        instrumentation.runOnMainSync { view.loadUrl("https://another.internal.test/next") }
        waitUntil(10_000) { finished.get() > 0 }
        js("window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun disposalRejectsRetainedInterfaceEvenBeforeRemovalTakesEffect() {
        show()
        js("window.retainedBridge=window.NativeBridge;true")
        instrumentation.runOnMainSync { bridge.dispose(); bridge.dispose() }
        js("window.retainedBridge.closeWebView();true")
        instrumentation.runOnMainSync { assertEquals(0, closes.get()) }
    }

    @Test fun sameUrlCanceledReloadDoesNotRequireJavascriptReinitialization() {
        show()
        finished.set(0)
        instrumentation.runOnMainSync { view.stopLoading(); view.loadUrl("$origin/form") }
        waitUntil(10_000) { finished.get() > 0 }
        js("window.NativeBridge.closeWebView();true")
        waitUntil(5_000) { closes.get() == 1 }
    }
}
