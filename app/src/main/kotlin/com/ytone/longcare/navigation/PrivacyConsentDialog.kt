package com.ytone.longcare.navigation

import android.app.Activity
import android.webkit.WebView
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import com.ytone.longcare.platform.webview.SecureWebViewClient
import com.ytone.longcare.platform.webview.WebPageState
import com.ytone.longcare.platform.webview.applySecureSettings
import com.ytone.longcare.platform.webview.loadOriginalWebUrl
import com.ytone.longcare.privacy.AgreementUrls

/**
 * 首次启动的隐私政策同意弹窗。
 */
@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    ReportStartupRootDrawn(
        expectedRoot = StartupRoot.Privacy,
        actualReadiness = resolveStartupRootReadiness(
            entryState = AppEntryState.ConsentRequired,
            userIdentity = null,
        ),
    )
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val linkColor = MaterialTheme.colorScheme.primary
    val welcomeText = stringResource(R.string.privacy_consent_welcome, appName)
    val introText = stringResource(R.string.privacy_consent_intro)
    val userAgreementText = stringResource(R.string.privacy_consent_user_agreement)
    val andText = stringResource(R.string.privacy_consent_and)
    val privacyPolicyText = stringResource(R.string.privacy_consent_policy)
    val explanationIntroText = stringResource(R.string.privacy_consent_explanation_intro)
    val explanationItemsText = stringResource(R.string.privacy_consent_explanation_items)
    val finalText = stringResource(R.string.privacy_consent_final)

    var webViewUrl by remember { mutableStateOf<String?>(null) }

    // 全屏 WebView 弹窗
    webViewUrl?.let { url ->
        InAppWebViewDialog(
            url = url,
            onDismiss = { webViewUrl = null }
        )
    }

    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("profile_privacy_root"),
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = stringResource(R.string.privacy_consent_title),
                modifier = Modifier.testTag("profile_privacy_title"),
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
                    append(welcomeText)
                    append(introText)

                    pushStringAnnotation(tag = "URL", annotation = AgreementUrls.USER_AGREEMENT_URL)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append(userAgreementText)
                    }
                    pop()

                    append(andText)

                    pushStringAnnotation(tag = "URL", annotation = AgreementUrls.PRIVACY_POLICY_URL)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append(privacyPolicyText)
                    }
                    pop()

                    append(explanationIntroText)
                    append(explanationItemsText)
                    append(finalText)
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
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("profile_privacy_accept"),
            ) {
                Text(
                    text = stringResource(R.string.privacy_consent_agree),
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
                },
                modifier = Modifier.testTag("profile_privacy_reject"),
            ) {
                Text(
                    text = stringResource(R.string.privacy_consent_disagree),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InAppWebViewDialog(
    url: String,
    onDismiss: () -> Unit
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
                                contentDescription = stringResource(R.string.common_back)
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
                            applySecureSettings(javaScriptRequired = false)
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
                    Column(
                        modifier = Modifier.fillMaxSize().wrapContentSize(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(text = stringResource(R.string.webview_load_failed))
                        TextButton(
                            onClick = {
                                webViewRef?.let { webView ->
                                    loadOriginalWebUrl(webView, url) { pageState = it }
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.webview_retry))
                        }
                    }
                }
            }
        }
    }
}
