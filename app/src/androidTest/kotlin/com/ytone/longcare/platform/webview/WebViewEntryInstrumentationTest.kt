package com.ytone.longcare.platform.webview

import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.navigation.InAppWebViewDialog
import java.util.concurrent.atomic.AtomicReference
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewEntryInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun businessEntryAllowsCrossDomainNavigationAndRetriesOriginalFailure() {
        verifyEntry(
            javaScriptExpected = true,
            content = {
                WebViewScreen(
                    actions = WebViewActions(onNavigateBack = {}),
                    url = RETRY_URL,
                    title = "business-web-smoke",
                )
            },
        )
    }

    @Test
    fun privacyEntryAllowsCrossDomainNavigationAndRetriesOriginalFailure() {
        verifyEntry(
            javaScriptExpected = false,
            content = {
                InAppWebViewDialog(
                    url = RETRY_URL,
                    onDismiss = {},
                )
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun verifyEntry(
        javaScriptExpected: Boolean,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent(content)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(LOAD_FAILED_TEXT)
                .fetchSemanticsNodes().isNotEmpty()
        }

        val loadedUrl = AtomicReference<String?>()
        onView(isAssignableFrom(WebView::class.java)).perform(
            onWebView { webView ->
                assertEquals(javaScriptExpected, webView.settings.javaScriptEnabled)
                assertFalse(webView.settings.allowFileAccess)
                assertFalse(webView.settings.allowContentAccess)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
                assertFalse(
                    webView.webViewClient.shouldOverrideUrlLoading(
                        webView,
                        "https://cross-domain.example.org/redirected",
                    ),
                )
                assertTrue(
                    webView.webViewClient.shouldOverrideUrlLoading(
                        webView,
                        "intent://external/#Intent;end",
                    ),
                )
                loadedUrl.set(webView.originalUrl ?: webView.url)
            },
        )
        assertEquals(RETRY_URL, loadedUrl.get())

        composeRule.onNodeWithText(RETRY_TEXT).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(LOAD_FAILED_TEXT)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onView(isAssignableFrom(WebView::class.java)).perform(
            onWebView { webView ->
                assertEquals(RETRY_URL, webView.originalUrl ?: webView.url)
            },
        )
    }

    private fun onWebView(assertion: (WebView) -> Unit): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

        override fun getDescription(): String = "inspect production WebView entry"

        override fun perform(uiController: UiController, view: View) {
            assertion(view as WebView)
            uiController.loopMainThreadUntilIdle()
        }
    }

    private companion object {
        const val RETRY_URL = "http://127.0.0.1:9/original-retry"
        const val LOAD_FAILED_TEXT = "网页加载失败，请检查网络后重试"
        const val RETRY_TEXT = "重新加载"
    }
}
