package com.ytone.longcare.platform.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/** Internal H5 API. Add future JavaScript methods here with explicit annotations. */
@Keep
internal class NativeBridge(
    private val isActive: () -> Boolean,
    private val onClose: () -> Unit,
    private val onEnterUserDetails: ((Int) -> Unit)? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false

    @JavascriptInterface
    fun closeWebView() {
        navigateOnce(onClose)
    }

    @JavascriptInterface
    fun enterUserDetails(pingguuserid: String?) {
        val customerId = pingguuserid?.toIntOrNull()?.takeIf { it > 0 } ?: return
        val openDetails = onEnterUserDetails ?: return
        navigateOnce { openDetails(customerId) }
    }

    private fun navigateOnce(action: () -> Unit) {
        handler.post {
            if (!closed && isActive()) {
                closed = true
                action()
            }
        }
    }

    /** Called on the UI thread before the owning WebView is released. */
    fun dispose() {
        closed = true
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val NAME = "NativeBridge"
    }
}
