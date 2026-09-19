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
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false

    @JavascriptInterface
    fun closeWebView() {
        handler.post {
            if (!closed && isActive()) {
                closed = true
                onClose()
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
