package com.ytone.longcare.features.webview.ui

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.R
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.platform.webview.NativeBridge
import com.ytone.longcare.platform.webview.ManagedWebViewClient

/** A route request owns its WebView; redirects never cause recomposition reloads. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    actions: WebViewActions,
    url: String,
    title: String,
) {
    val currentActions by rememberUpdatedState(actions)
    val lifecycle by rememberUpdatedState(LocalLifecycleOwner.current.lifecycle)
    key(url) {
        var isLoading by remember { mutableStateOf(true) }
        var rendererGone by remember { mutableStateOf(false) }
        var pageError by remember { mutableStateOf<Int?>(null) }
        var bridge by remember { mutableStateOf<NativeBridge?>(null) }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { currentActions.onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back_button_description))
                    }
                },
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                pageError?.let {
                    Text(stringResource(it),
                        modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (rendererGone) {
                        Text(stringResource(R.string.webview_renderer_unavailable),
                            Modifier.fillMaxWidth().padding(24.dp))
                    } else AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = ManagedWebViewClient(
                                    onLoadingChanged = { loading ->
                                        isLoading = loading
                                        if (loading) pageError = null
                                    },
                                    onLoadFailed = {
                                        isLoading = false
                                        pageError = R.string.webview_load_failed
                                    },
                                    onRendererGone = {
                                        rendererGone = true
                                        isLoading = false
                                        bridge?.dispose()
                                        removeJavascriptInterface(NativeBridge.NAME)
                                        bridge = null
                                        // ManagedWebViewClient immediately retires the dead view.
                                    },
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                val nativeBridge = NativeBridge(
                                    isActive = {
                                        !rendererGone && lifecycle.currentState == Lifecycle.State.RESUMED &&
                                            currentActions.isCurrentPage()
                                    },
                                    onClose = { currentActions.onCloseFromH5() },
                                )
                                bridge = nativeBridge
                                addJavascriptInterface(nativeBridge, NativeBridge.NAME)
                                loadUrl(url)
                            }
                        },
                        onRelease = { view ->
                            bridge?.dispose()
                            bridge = null
                            if (!rendererGone) {
                                view.removeJavascriptInterface(NativeBridge.NAME)
                                view.stopLoading()
                                view.loadUrl("about:blank")
                                view.clearHistory()
                                view.removeAllViews()
                                view.destroy()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isLoading) CircularProgressIndicator(Modifier.fillMaxSize().wrapContentSize())
                }
            }
        }
    }
}
