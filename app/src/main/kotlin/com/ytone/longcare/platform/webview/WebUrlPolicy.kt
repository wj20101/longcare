package com.ytone.longcare.platform.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import java.net.URI

object WebUrlPolicy {
    fun isAllowed(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank() || rawUrl != rawUrl.trim()) return false
        if (rawUrl.any(Char::isWhitespace) || rawUrl.any { it.code < 0x20 } || '\\' in rawUrl) {
            return false
        }
        val javaUri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val androidUri = runCatching { rawUrl.toUri() }.getOrNull() ?: return false
        val scheme = androidUri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (androidUri.host.isNullOrBlank() || javaUri.rawAuthority.isNullOrBlank()) return false
        return hasValidPort(javaUri.rawAuthority)
    }

    fun shouldBlock(rawUrl: String?): Boolean = !isAllowed(rawUrl)

    private fun hasValidPort(rawAuthority: String): Boolean {
        val hostAndPort = rawAuthority.substringAfterLast('@')
        val portText = when {
            hostAndPort.startsWith('[') -> {
                val closingBracket = hostAndPort.indexOf(']')
                if (closingBracket <= 1) return false
                val suffix = hostAndPort.substring(closingBracket + 1)
                when {
                    suffix.isEmpty() -> return true
                    suffix.startsWith(':') -> suffix.drop(1)
                    else -> return false
                }
            }
            hostAndPort.count { it == ':' } > 1 -> return false
            ':' in hostAndPort -> hostAndPort.substringAfterLast(':')
            else -> return true
        }
        if (portText.isEmpty() || portText.any { !it.isDigit() }) return false
        return portText.toIntOrNull() in 1..65_535
    }
}

internal sealed interface WebPageState {
    data object Loading : WebPageState
    data object Ready : WebPageState
    data class Failed(val reason: WebPageFailure) : WebPageState
}

internal enum class WebPageFailure {
    INVALID_URL,
    NETWORK,
    HTTP,
    SSL,
}

internal class SecureWebViewClient(
    private val policy: WebUrlPolicy = WebUrlPolicy,
    private val onStateChanged: (WebPageState) -> Unit,
) : WebViewClient() {
    private var mainFrameFailed = false

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        policy.shouldBlock(request?.url?.toString())

    @Deprecated("Deprecated in Android")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        policy.shouldBlock(url)

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        mainFrameFailed = false
        onStateChanged(WebPageState.Loading)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (!mainFrameFailed) onStateChanged(WebPageState.Ready)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame == true) fail(WebPageFailure.NETWORK)
    }

    @Deprecated("Deprecated in Android")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        fail(WebPageFailure.NETWORK)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
            fail(WebPageFailure.HTTP)
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        handler?.cancel()
        fail(WebPageFailure.SSL)
    }

    private fun fail(reason: WebPageFailure) {
        mainFrameFailed = true
        onStateChanged(WebPageState.Failed(reason))
    }
}

internal fun WebView.applySecureSettings(javaScriptRequired: Boolean) {
    settings.apply {
        javaScriptEnabled = javaScriptRequired
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
}

internal fun loadOriginalWebUrl(
    webView: WebView,
    originalUrl: String,
    onStateChanged: (WebPageState) -> Unit,
): Boolean {
    if (!WebUrlPolicy.isAllowed(originalUrl)) {
        onStateChanged(WebPageState.Failed(WebPageFailure.INVALID_URL))
        return false
    }
    onStateChanged(WebPageState.Loading)
    webView.loadUrl(originalUrl)
    return true
}
