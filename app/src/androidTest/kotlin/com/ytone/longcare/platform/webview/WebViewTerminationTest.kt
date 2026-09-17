package com.ytone.longcare.platform.webview

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.navigation.InAppWebViewDialog
import com.ytone.longcare.navigation.NavigationStateTestActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Delivers a controlled renderer-exit callback without crashing a shared emulator process. */
@SdkSuppress(minSdkVersion = 29)
class WebViewTerminationTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()

    private fun find(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
        else -> null
    }

    private fun currentWebView(): WebView? =
        WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)

    private fun signalRendererExit(expectJavascript: Boolean) {
        lateinit var removed: WebView
        compose.runOnIdle {
            removed = requireNotNull(currentWebView())
            assertEquals(expectJavascript, removed.settings.javaScriptEnabled)
            removed.stopLoading()
            assertTrue(removed.webViewClient.onRenderProcessGone(removed, null))
            // Removal cannot wait for a future Compose frame (the host may be backgrounded).
            assertNull(removed.parent)
        }
        compose.onNodeWithText("网页已停止运行，请返回后重新打开").assertIsDisplayed()
        compose.runOnIdle {
            assertNull(currentWebView())
            assertNull(removed.parent)
        }
    }

    @Test fun evaluationRendererExitRemovesWebViewAndKeepsNativeBack() {
        var backs = 0
        compose.setContent {
            WebViewScreen(WebViewActions({ backs++ }), "https://evaluation.invalid/form", "表单评估")
        }
        signalRendererExit(expectJavascript = true)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun privacyRendererExitKeepsConsentPendingAndDismissAvailable() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            if (visible.value) InAppWebViewDialog("https://privacy.invalid/agreement") {
                dismissals++
                visible.value = false
            } else Text("隐私同意仍待确认")
        }
        signalRendererExit(expectJavascript = true)
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("隐私同意仍待确认").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
