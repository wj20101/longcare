package com.ytone.longcare.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ytone.longcare.R

private const val USER_AGREEMENT_URL = "https://www.ytone.com.cn/longcare/xieyi/user.html"
private const val PRIVACY_POLICY_URL = "https://www.ytone.com.cn/longcare/xieyi/yinsi.html"

/**
 * 首次启动的隐私政策同意弹窗。
 */
@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val linkColor = MaterialTheme.colorScheme.primary

    var webViewUrl by remember { mutableStateOf<String?>(null) }

    // 全屏 WebView 弹窗
    webViewUrl?.let { url ->
        InAppWebViewDialog(
            url = url,
            onDismiss = { webViewUrl = null }
        )
    }

    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "用户协议与隐私政策",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val annotatedString = buildAnnotatedString {
                    append("欢迎使用${appName}！\n\n")
                    append("我们非常重视您的个人信息和隐私保护。在您使用我们的服务前，请仔细阅读并了解")

                    pushStringAnnotation(tag = "URL", annotation = USER_AGREEMENT_URL)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append("《用户服务协议》")
                    }
                    pop()

                    append("和")

                    pushStringAnnotation(tag = "URL", annotation = PRIVACY_POLICY_URL)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append("《隐私政策》")
                    }
                    pop()

                    append("。\n\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append("我们将通过上述协议向您说明：\n")
                    }
                    append("(1)《隐私政策》中关于个人设备用户信息的收集和使用的说明。\n")
                    append("(2)《隐私政策》中与第三方SDK类服务商数据共享、相关信息收集和使用说明。\n\n")
                    append("如您同意以上协议内容，请点击\"同意并继续\"开始使用我们的服务。")
                }

                @Suppress("DEPRECATION")
                androidx.compose.foundation.text.ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                webViewUrl = annotation.item
                            }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAgree,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "同意并继续",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDisagree()
                    (context as? Activity)?.finish()
                }
            ) {
                Text(
                    text = "不同意",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppWebViewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
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
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?, request: WebResourceRequest?
                                ): Boolean {
                                    val scheme = request?.url?.scheme?.lowercase()
                                    return scheme != "http" && scheme != "https"
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                javaScriptCanOpenWindowsAutomatically = false
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            if (isSafeHttpUrl(url)) {
                                loadUrl(url)
                            }
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
}

private fun isSafeHttpUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val scheme = runCatching { url.toUri().scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}
