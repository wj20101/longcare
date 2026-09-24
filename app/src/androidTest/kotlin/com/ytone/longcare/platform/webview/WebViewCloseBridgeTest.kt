package com.ytone.longcare.platform.webview

import android.net.Uri
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.ytone.longcare.features.sales.SalesEvaluationCompleteScreen
import com.ytone.longcare.features.sales.SalesPageBackground
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.filters.SdkSuppress
import com.ytone.longcare.navigation.*
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.privacy.AgreementUrls
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real containers; first-party HTTPS responses are supplied locally without customer requests. */
@SdkSuppress(minSdkVersion = 29)
class WebViewCloseBridgeTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()
    private lateinit var webView: WebView
    private val closes = AtomicInteger()
    private val finished = AtomicInteger()
    private val finishedUrl = AtomicReference<String?>()
    private val origin = "https://careweb.ytone.cn"

    private fun setContent(content: @Composable () -> Unit) = compose.setContent(content)

    private fun client(html: String, delegate: WebViewClient) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) =
            delegate.shouldOverrideUrlLoading(view, request)
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
            delegate.shouldInterceptRequest(view, request)
                ?: WebResourceResponse("text/html", "UTF-8", html.byteInputStream())
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) =
            delegate.onReceivedError(view, request, error)
        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) =
            delegate.onReceivedHttpError(view, request, response)
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) =
            delegate.onPageStarted(view, url, favicon)
        override fun onPageFinished(view: WebView?, url: String?) {
            delegate.onPageFinished(view, url)
            finishedUrl.set(url)
            finished.incrementAndGet()
        }
    }

    private fun js(script: String): String {
        val result = AtomicReference<String?>()
        compose.runOnUiThread { webView.evaluateJavascript(script) { result.set(it) } }
        compose.waitUntil(5_000) { result.get() != null }
        return result.get()!!
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun supplyLocalContent(url: String) {
        finished.set(0)
        finishedUrl.set(null)
        compose.runOnIdle {
            webView = requireNotNull(WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView))
            webView.stopLoading()
            webView.webViewClient = client("<h1>Mock 评估表单</h1>", webView.webViewClient)
            webView.loadUrl(url)
        }
        compose.waitUntil(10_000) { finishedUrl.get() == url }
        // stopLoading may finish the original (same-URL) network request after our reload.
        // Wait for the controlled document, not just
        // the URL callback of that canceled request.
        val ready = AtomicReference(false)
        compose.waitUntil(10_000) {
            compose.runOnUiThread {
                webView.evaluateJavascript("""
                    document.body && document.body.textContent.indexOf('Mock 评估表单') >= 0 &&
                      !!window.NativeBridge && typeof window.NativeBridge.closeWebView === 'function'
                """.trimIndent()) { ready.set(it == "true") }
            }
            ready.get()
        }
    }

    @Test fun productionContainerKeepsRedirectOnRecompositionAndRecreatesForNewRequest() {
        val title = mutableStateOf("表单评估")
        val url = mutableStateOf("$origin/form")
        setContent {
            WebViewScreen(WebViewActions({ closes.incrementAndGet() }), url.value, title.value)
        }
        supplyLocalContent("$origin/redirected")
        val first = webView
        assertEquals("\"function\"", js("typeof window.NativeBridge.closeWebView"))
        compose.runOnIdle { title.value = "评估详情" }
        compose.runOnIdle {
            assertSame(first, findWebView(compose.activity.window.decorView))
            assertEquals("$origin/redirected", first.url)
        }
        compose.runOnIdle { url.value = "$origin/second-form" }
        supplyLocalContent("$origin/controlled-second-form")
        assertNotSame(first, webView)
        assertEquals("\"function\"", js("typeof window.NativeBridge.closeWebView"))
        js("window.NativeBridge.closeWebView();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun productionContainerAllowsCrossOriginNavigationAndClose() {
        setContent { WebViewScreen(WebViewActions({ closes.incrementAndGet() }), "$origin/form", "表单评估") }
        supplyLocalContent("$origin/form")
        finishedUrl.set(null)
        js("window.location.href='https://another.internal.test/next';true")
        compose.waitUntil(10_000) { finishedUrl.get() == "https://another.internal.test/next" }
        js("window.NativeBridge.closeWebView();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun productionContainerRegistersForAnyInitialOrigin() {
        val url = "https://another.internal.test/form"
        setContent { WebViewScreen(WebViewActions({ closes.incrementAndGet() }), url, "网页") }
        supplyLocalContent(url)
        js("window.NativeBridge.closeWebView();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun httpErrorCallbackDoesNotDisableClose() {
        setContent { WebViewScreen(WebViewActions({ closes.incrementAndGet() }), "$origin/form", "表单评估", showNativeToolbar = false) }
        supplyLocalContent("$origin/error")
        compose.runOnIdle { webView.webViewClient.onPageStarted(webView, "$origin/error", null) }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        // A locally intercepted response does not reliably produce the platform HTTP-error
        // callback. Deliver that callback explicitly to the real production client.
        compose.runOnIdle {
            val request = object : WebResourceRequest {
                override fun getUrl() = Uri.parse("$origin/error")
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = emptyMap<String, String>()
            }
            webView.webViewClient.onReceivedHttpError(
                webView, request,
                WebResourceResponse("text/html", "UTF-8", 500, "Error", emptyMap(), "".byteInputStream()),
            )
        }
        compose.onNodeWithText("网页加载失败，请返回后重新打开。").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        js("window.NativeBridge.closeWebView();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
    }

    @Test fun realNavigation3ReturnsToHomeAndPreservesSourceState() {
        lateinit var navigator: AppNavigator
        setContent {
            AppNavigationHost(HomeRoute, "mock-sales") { nav ->
                navigator = nav
                AppEntryProviderBuilder().apply {
                    destination<HomeRoute> {
                        val customer by rememberSaveable { mutableStateOf("Mock 客户详情") }
                        Text(customer)
                    }
                    destination<WebViewRoute> { entry ->
                        val page = nav.forEntry(entry.id)
                        WebViewScreen(
                            WebViewActions(
                                { closes.incrementAndGet(); page.popBackStack() },
                                page::canHandleCallback,
                            ), "$origin/report", "评估报告", showNativeToolbar = false,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { navigator.navigate(WebViewRoute("$origin/report", "评估报告")) }
        supplyLocalContent("$origin/controlled-report")
        compose.runOnUiThread {
            webView.evaluateJavascript("window.NativeBridge.closeWebView();window.NativeBridge.closeWebView();", null)
        }
        compose.waitUntil(5_000) { closes.get() == 1 }
        compose.onNodeWithText("Mock 客户详情").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, navigator.backStack.size); assertEquals(1, closes.get()) }
    }

    @Test fun realH5DetailBridgeReplacesWebEntryAndBackRetainsResultRecord() {
        lateinit var navigator: AppNavigator
        val result = SalesRoute(com.ytone.longcare.presentation.sales.SalesPage.EVALUATION_COMPLETE, 7, recordId = "record-7")
        setContent {
            AppNavigationHost(HomeRoute, "offline-details") { nav ->
                navigator = nav
                AppEntryProviderBuilder().apply {
                    destination<HomeRoute> { Text("首页") }
                    destination<SalesRoute> { entry ->
                        val route = entry.route<SalesRoute>()
                        if (route.page == com.ytone.longcare.presentation.sales.SalesPage.CUSTOMER_DETAIL) {
                            com.ytone.longcare.features.sales.SalesCustomerDetailScreen(
                                customer = com.ytone.longcare.model.UserLatentDetailModel(id = route.customerId, userName = "目标客户 ${route.customerId}"),
                                isLoading = false, errorMessage = null,
                                onBack = { nav.forEntry(entry.id).popBackStack() },
                                onRetry = {}, onEvaluate = {}, onOpenReport = {},
                            )
                        } else Text("结果 ${route.customerId} / ${route.recordId}")
                    }
                    registerWebViewRoute(nav)
                }
            }
        }
        compose.runOnIdle { navigator.navigate(result) }
        compose.runOnIdle { navigator.navigateToEvaluationReport("https://report.invalid", "报告") }
        supplyLocalContent("https://report.invalid/controlled")
        js("window.NativeBridge.enterUserDetails('8');window.NativeBridge.closeWebView();true")
        compose.onNodeWithText("目标客户 8").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(HomeRoute, result, SalesRoute(com.ytone.longcare.presentation.sales.SalesPage.CUSTOMER_DETAIL, 8)),
                navigator.backStack.map { (it as AppNavEntry).route })
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("结果 7 / record-7").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, navigator.backStack.size) }
    }

    @Test fun evaluationJsCloseShowsGradePageWhileNativeBackOnlyCloses() {
        lateinit var navigator: AppNavigator
        val completed = mutableStateOf(false)
        var resultQueries = 0
        setContent {
            AppNavigationHost(HomeRoute, "mock-completion") { nav ->
                navigator = nav
                AppEntryProviderBuilder().apply {
                    destination<HomeRoute> {
                        if (completed.value) {
                            var grade by remember { mutableStateOf<String?>(null) }
                            LaunchedEffect(Unit) {
                                resultQueries++
                                grade = "A级" // Controlled result response; no customer API request.
                            }
                            SalesPageBackground {
                                SalesEvaluationCompleteScreen(
                                    hasReport = false, grade = grade,
                                    onBack = { completed.value = false },
                                    onDone = { completed.value = false },
                                    onOpenReport = {},
                                )
                            }
                        } else Text("Mock 评估入口")
                    }
                    destination<WebViewRoute> { entry ->
                        val page = nav.forEntry(entry.id)
                        WebViewScreen(
                            WebViewActions(
                                onNavigateBack = { page.popBackStack() },
                                isCurrentPage = page::canHandleCallback,
                                onCloseFromH5 = {
                                    completed.value = true
                                    closes.incrementAndGet()
                                    page.popBackStack()
                                },
                            ), "$origin/form", "表单评估", showNativeToolbar = false,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { navigator.navigate(WebViewRoute("$origin/form", "表单评估", true)) }
        supplyLocalContent("$origin/form")
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Mock 评估入口").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, resultQueries); assertFalse(completed.value) }
        compose.runOnIdle { navigator.navigate(WebViewRoute("$origin/form", "表单评估", true)) }
        supplyLocalContent("$origin/form")
        compose.runOnUiThread {
            webView.evaluateJavascript("window.NativeBridge.closeWebView();window.NativeBridge.closeWebView();", null)
        }
        compose.onNodeWithText("评估成功，评估等级为：A级").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, resultQueries)
            assertEquals(1, closes.get())
            assertEquals(1, navigator.backStack.size)
        }
        compose.onNodeWithText("完成").performScrollTo().performClick()
        compose.onNodeWithText("Mock 评估入口").assertIsDisplayed()
    }

    @Test fun privacyAndAgreementJavascriptCloseReturnToPendingConsent() {
        val agrees = AtomicInteger()
        val disagrees = AtomicInteger()
        setContent {
            PrivacyConsentDialog(onAgree = { agrees.incrementAndGet() }, onDisagree = { disagrees.incrementAndGet() })
        }
        listOf("《用户服务协议》" to AgreementUrls.USER_AGREEMENT_URL,
            "《隐私政策》" to AgreementUrls.PRIVACY_POLICY_URL).forEach { (label, url) ->
            val text = compose.onNodeWithText("欢迎使用", substring = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val index = layout.layoutInput.text.text.indexOf(label)
            assertTrue(index >= 0)
            text.performTouchInput { click(layout.getBoundingBox(index + 1).center) }
            supplyLocalContent(url)
            compose.runOnIdle {
                assertTrue(webView.settings.javaScriptEnabled)
                assertFalse(webView.settings.allowFileAccess)
                assertFalse(webView.settings.allowContentAccess)
            }
            assertEquals("\"function\"", js("typeof window.NativeBridge.closeWebView"))
            compose.runOnUiThread {
                webView.evaluateJavascript("window.NativeBridge.closeWebView();window.NativeBridge.closeWebView();", null)
            }
            compose.waitUntil(5_000) {
                WindowInspector.getGlobalWindowViews().none { findWebView(it) != null }
            }
            compose.onNodeWithText("用户协议与隐私政策").assertIsDisplayed()
            assertEquals(0, agrees.get())
            assertEquals(0, disagrees.get())
        }
    }

    @Test fun privacyDialogUsesLatestDismissCallbackAfterRecomposition() {
        val updated = mutableStateOf(false)
        val oldDismissals = AtomicInteger()
        setContent {
            val current = updated.value
            InAppWebViewDialog("$origin/agreement") {
                if (current) closes.incrementAndGet() else oldDismissals.incrementAndGet()
            }
        }
        supplyLocalContent("$origin/agreement")
        val first = webView
        compose.runOnIdle { updated.value = true }
        compose.runOnIdle {
            assertSame(first, WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView))
        }
        js("window.NativeBridge.closeWebView();window.NativeBridge.closeWebView();true")
        compose.waitUntil(5_000) { closes.get() == 1 }
        assertEquals(0, oldDismissals.get())
    }
}
