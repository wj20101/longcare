package com.ytone.longcare.features.webview.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.ytone.longcare.R

/**
 * WebView页面
 * 用于显示用户协议、隐私政策等网页内容
 *
 * @param navController 导航控制器
 * @param url 要加载的网页URL
 * @param title 页面标题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    navController: NavController,
    url: String,
    title: String
) {
    var isLoading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize()
                )
            }
        }
    }
}
