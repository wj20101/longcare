package com.ytone.longcare.platform.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/** Retires a dead renderer immediately, including while Compose is paused in the background. */
// WebKit 1.17.0 RenderProcessGoneDetector.visitConstructor reports the super call even
// though this final class overrides the callback below. WebViewTerminationTest covers
// both owners' removal, disposal and native back. Remove when the detector is fixed.
@SuppressLint("MissingOnRenderProcessGone")
internal class ManagedWebViewClient(
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onLoadFailed: () -> Unit,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = onLoadingChanged(true)

    override fun onPageFinished(view: WebView?, url: String?) = onLoadingChanged(false)

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) onLoadFailed()
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        if (request?.isForMainFrame == true) onLoadFailed()
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onRendererGone()
        (view?.parent as? ViewGroup)?.removeView(view)
        view?.removeAllViews()
        view?.destroy()
        return true
    }
}
