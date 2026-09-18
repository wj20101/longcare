package com.ytone.longcare.platform.webview

import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.navigation.NavigationStateTestActivity
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real WebView on API 24+, using a local document without customer requests. */
class WebViewPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()
    private lateinit var webView: WebView
    private val closes = AtomicInteger()
    private val backs = AtomicInteger()
    private val showToolbar = mutableStateOf(false)
    private var defaultLightStatusBars = false
    private var defaultLightNavigationBars = false

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun showPage(fontScale: Float = 1f) {
        val html = """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0"><header style="height:48px;background:#eee">
            <button id="back" onclick="window.NativeBridge.closeWebView()">返回</button>H5 评估标题
            </header><button id="confirm" onclick="document.getElementById('result').textContent='已刷新'">确认</button>
            <div id="result">待确认</div><input id="answer" aria-label="测试输入" /></body></html>
        """.trimIndent()
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            defaultLightStatusBars = controller.isAppearanceLightStatusBars
            defaultLightNavigationBars = controller.isAppearanceLightNavigationBars
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                WebViewScreen(
                    actions = WebViewActions(
                        onNavigateBack = { backs.incrementAndGet() },
                        onCloseFromH5 = { closes.incrementAndGet() },
                    ),
                    url = "data:text/html;charset=utf-8," + Uri.encode(html),
                    title = "原生标题",
                    showNativeToolbar = showToolbar.value,
                )
            }
        }
        compose.runOnIdle { webView = requireNotNull(findWebView(compose.activity.window.decorView)) }
        val ready = AtomicReference(false)
        compose.waitUntil(10_000) {
            compose.runOnUiThread {
                webView.evaluateJavascript("!!document.getElementById('back')") { ready.set(it == "true") }
            }
            ready.get()
        }
    }

    private fun js(script: String): String {
        val result = AtomicReference<String?>()
        compose.runOnUiThread { webView.evaluateJavascript(script) { result.set(it) } }
        compose.waitUntil(5_000) { result.get() != null }
        return result.get()!!
    }

    @Test fun fullscreenUsesSafeContentBoundsWithoutNativeToolbar() {
        showPage(fontScale = 1.5f)
        compose.onNodeWithText("原生标题").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        assertEquals("true", js("document.querySelector('header').textContent.indexOf('H5 评估标题') >= 0"))
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val location = IntArray(2).also(webView::getLocationOnScreen)
            val rootLocation = IntArray(2).also(root::getLocationOnScreen)
            assertEquals((rootLocation[1] + insets.top).toFloat(), location[1].toFloat(), 2f)
            assertEquals((rootLocation[0] + insets.left).toFloat(), location[0].toFloat(), 2f)
            assertEquals((root.width - insets.left - insets.right).toFloat(), webView.width.toFloat(), 2f)
            assertEquals((root.height - insets.top - insets.bottom).toFloat(), webView.height.toFloat(), 2f)
        }
    }

    @Test fun confirmRefreshesWithoutClosingAndH5BackClosesOnlyOnce() {
        showPage()
        assertEquals("\"已刷新\"", js("document.getElementById('confirm').click();document.getElementById('result').textContent"))
        compose.runOnIdle { assertEquals(0, closes.get()); assertEquals(0, backs.get()) }
        js("document.getElementById('back').click();document.getElementById('back').click();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
        assertEquals(0, backs.get())
    }

    @Test fun fullscreenSystemBarsAreReadableAndRestoreOnExit() {
        showPage()
        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertTrue(controller.isAppearanceLightStatusBars)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertTrue(controller.isAppearanceLightNavigationBars)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertFalse(window.isNavigationBarContrastEnforced)
            }
            assertTrue(requireNotNull(ViewCompat.getRootWindowInsets(window.decorView))
                .isVisible(WindowInsetsCompat.Type.statusBars()))
            showToolbar.value = true
        }
        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(defaultLightStatusBars, controller.isAppearanceLightStatusBars)
            assertEquals(defaultLightNavigationBars, controller.isAppearanceLightNavigationBars)
        }
    }

    @Test fun keyboardResizesContentAndRestoresWithoutReloadOrGhostPadding() {
        showPage()
        var initialHeight = 0
        compose.runOnIdle { initialHeight = webView.height }
        js("document.getElementById('answer').focus();window.presentationMarker=42")
        compose.runOnUiThread {
            webView.requestFocus()
            WindowCompat.getInsetsController(compose.activity.window, webView)
                .show(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10_000) {
            val visible = AtomicReference(false)
            compose.runOnUiThread {
                val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                visible.set(insets?.isVisible(WindowInsetsCompat.Type.ime()) == true && webView.height < initialHeight)
            }
            visible.get()
        }
        assertEquals("\"answer\"", js("document.activeElement.id"))
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(root))
            val location = IntArray(2).also(webView::getLocationInWindow)
            assertEquals((root.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom).toFloat(),
                (location[1] + webView.height).toFloat(), 2f)
        }
        compose.runOnUiThread {
            WindowCompat.getInsetsController(compose.activity.window, webView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10_000) {
            val restored = AtomicReference(false)
            compose.runOnUiThread { restored.set(webView.height == initialHeight) }
            restored.get()
        }
        assertEquals("42", js("window.presentationMarker"))
        assertEquals(0, closes.get())
    }

    @Test fun ordinaryPageKeepsNativeToolbarAndPresentationChangeDoesNotReload() {
        showToolbar.value = true
        showPage()
        compose.onNodeWithText("原生标题").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs.get())
        assertEquals(0, closes.get())
        js("window.presentationMarker=42")
        compose.runOnIdle { showToolbar.value = false }
        compose.onNodeWithText("原生标题").assertDoesNotExist()
        compose.runOnIdle { assertSame(webView, findWebView(compose.activity.window.decorView)) }
        assertEquals("42", js("window.presentationMarker"))
    }
}
