package com.ytone.longcare.features.webview.ui

import android.annotation.SuppressLint
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
import com.ytone.longcare.R
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.platform.webview.SecureWebViewClient
import com.ytone.longcare.platform.webview.WebPageState
import com.ytone.longcare.platform.webview.applySecureSettings
import com.ytone.longcare.platform.webview.loadOriginalWebUrl

/**
 * WebView页面
 * 用于显示用户协议、隐私政策等网页内容
 *
 * @param actions 页面动作集合
 * @param url 要加载的网页URL
 * @param title 页面标题
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    actions: WebViewActions,
    url: String,
    title: String
) {
    var pageState by remember(url) { mutableStateOf<WebPageState>(WebPageState.Loading) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadedOriginalUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.run {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = actions.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        webViewClient = SecureWebViewClient(onStateChanged = { pageState = it })
                        applySecureSettings(javaScriptRequired = true)
                        loadedOriginalUrl = url
                        loadOriginalWebUrl(this, url) { pageState = it }
                    }
                },
                update = { webView ->
                    if (loadedOriginalUrl != url) {
                        loadedOriginalUrl = url
                        loadOriginalWebUrl(webView, url) { pageState = it }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (pageState is WebPageState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                )
            }
            if (pageState is WebPageState.Failed) {
                WebFailureContent(
                    onRetry = {
                        webViewRef?.let { webView ->
                            loadOriginalWebUrl(webView, url) { pageState = it }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebFailureContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().wrapContentSize(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.webview_load_failed))
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.webview_retry))
        }
    }
}
