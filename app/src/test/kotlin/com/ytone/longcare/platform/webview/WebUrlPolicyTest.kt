package com.ytone.longcare.platform.webview

import android.content.Context
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class WebUrlPolicyTest {
    @Test
    fun `all valid HTTP and HTTPS hosts and cross-domain destinations are allowed`() {
        val allowed = listOf(
            "https://example.com",
            "https://sub.example.com/privacy",
            "https://another-vendor.example/report?id=1",
            "HTTPS://EXAMPLE.COM/path",
            "http://example.com",
            "http://127.0.0.1:8080/report",
            "https://[2001:db8::1]:8443/path",
            "https://localhost:9443",
            "https://xn--fsqu00a.xn--0zwm56d/path",
        )

        allowed.forEach { url ->
            assertTrue("Expected host-agnostic URL to be allowed: $url", WebUrlPolicy.isAllowed(url))
        }
    }

    @Test
    fun `hostless malformed local and executable schemes are blocked`() {
        val blocked = listOf<String?>(
            null,
            "",
            "   ",
            "https://",
            "https:///missing-host",
            "http:example.com",
            "https://exa mple.com",
            "https://example.com/%zz",
            "https://example.com:bad/path",
            "https://example.com:99999/path",
            "https://[::1",
            "file:///sdcard/secret.txt",
            "content://com.example.provider/item/1",
            "javascript:alert(1)",
            "intent://external/#Intent;end",
            "data:text/html,hello",
            "ftp://example.com/file",
            "custom://example.com/path",
            "//example.com/path",
        )

        blocked.forEach { url ->
            assertFalse("Expected URL to be blocked: $url", WebUrlPolicy.isAllowed(url))
        }
    }

    @Test
    fun `navigation callback delegates allowed cross-domain URL to WebView without loadUrl recursion`() {
        val states = mutableListOf<WebPageState>()
        val client = SecureWebViewClient(onStateChanged = states::add)
        val webView = mockk<WebView>(relaxed = true)

        assertFalse(client.shouldOverrideUrlLoading(webView, "https://redirected.example.org/next"))
        assertTrue(client.shouldOverrideUrlLoading(webView, "intent://external/#Intent;end"))
        verify(exactly = 0) { webView.loadUrl(any<String>()) }
        assertTrue(states.isEmpty())
    }

    @Test
    fun `SSL failure is cancelled and remains a retryable main-page error`() {
        val states = mutableListOf<WebPageState>()
        val client = SecureWebViewClient(onStateChanged = states::add)
        val handler = mockk<SslErrorHandler>(relaxed = true)

        client.onReceivedSslError(mockk(relaxed = true), handler, null)
        client.onPageFinished(mockk(relaxed = true), "https://example.com")

        verify(exactly = 1) { handler.cancel() }
        assertEquals(listOf(WebPageState.Failed(WebPageFailure.SSL)), states)
    }

    @Test
    fun `only main-document network errors replace the page state`() {
        val states = mutableListOf<WebPageState>()
        val client = SecureWebViewClient(onStateChanged = states::add)
        val subresource = mockk<WebResourceRequest> {
            every { isForMainFrame } returns false
        }
        val mainDocument = mockk<WebResourceRequest> {
            every { isForMainFrame } returns true
        }

        client.onReceivedError(mockk(relaxed = true), subresource, mockk(relaxed = true))
        client.onReceivedError(mockk(relaxed = true), mainDocument, mockk(relaxed = true))

        assertEquals(listOf(WebPageState.Failed(WebPageFailure.NETWORK)), states)
    }

    @Test
    fun `retry always loads the original URL and invalid input never reaches WebView`() {
        val webView = mockk<WebView>(relaxed = true)
        val states = mutableListOf<WebPageState>()
        val original = "https://origin.example/start"

        assertTrue(loadOriginalWebUrl(webView, original, states::add))
        assertTrue(loadOriginalWebUrl(webView, original, states::add))
        assertFalse(loadOriginalWebUrl(webView, "file:///secret", states::add))

        verify(exactly = 2) { webView.loadUrl(original) }
        verify(exactly = 0) { webView.loadUrl("file:///secret") }
        assertEquals(WebPageState.Failed(WebPageFailure.INVALID_URL), states.last())
    }

    @Test
    fun `business and privacy profiles keep dangerous WebView capabilities disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mockedSettings = mockk<WebSettings>(relaxed = true)
            val mockedWebView = mockk<WebView> {
                every { settings } returns mockedSettings
            }
            mockedWebView.applySecureSettings(javaScriptRequired = true)
            verify(exactly = 1) { mockedSettings.safeBrowsingEnabled = true }
            verify(exactly = 0) { mockedWebView.addJavascriptInterface(any(), any()) }
        }
        val business = WebView(context).apply { applySecureSettings(javaScriptRequired = true) }
        val privacy = WebView(context).apply { applySecureSettings(javaScriptRequired = false) }

        assertTrue(business.settings.javaScriptEnabled)
        assertFalse(privacy.settings.javaScriptEnabled)
        listOf(business, privacy).forEach { webView ->
            val settings = webView.settings
            assertFalse(settings.allowFileAccess)
            assertFalse(settings.allowContentAccess)
            @Suppress("DEPRECATION")
            assertFalse(settings.allowFileAccessFromFileURLs)
            @Suppress("DEPRECATION")
            assertFalse(settings.allowUniversalAccessFromFileURLs)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
            assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(settings.supportMultipleWindows())
            webView.destroy()
        }
    }

    @Test
    fun `network security config keeps global cleartext disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.network_security_config)
        var baseConfigCleartext: Boolean? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "base-config") {
                baseConfigCleartext = parser.getAttributeBooleanValue(
                    null,
                    "cleartextTrafficPermitted",
                    true,
                )
            }
            parser.next()
        }
        parser.close()

        assertEquals(false, baseConfigCleartext)
    }
}
