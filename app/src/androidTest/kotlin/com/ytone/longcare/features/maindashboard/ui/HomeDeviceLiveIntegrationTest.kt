package com.ytone.longcare.features.maindashboard.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.MainActivity
import com.ytone.longcare.R
import com.ytone.longcare.features.home.api.isHomeDevicePageUrl
import com.ytone.longcare.features.home.vm.HomeDeviceViewModel
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.theme.LongCareTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit online diagnostic: uses the existing account without changing it or its device binding. */
class HomeDeviceLiveIntegrationTest {
    @Test
    fun realDeviceStatusAndInstallationPageHaveVisibleContent() {
        // No Activity is launched by the default offline CI selection.
        assumeTrue("Online device/H5 diagnostic requires runHomeDeviceLive=true",
            InstrumentationRegistry.getArguments().getString("runHomeDeviceLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = requireNotNull(context.getExternalFilesDir("home-device-tests"))
        val report = JSONObject().put("success", false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var scenario: ActivityScenario<MainActivity>? = null
        var poll: Job? = null
        var failure: Throwable? = null
        val snapshots = JSONArray()

        fun signal(stage: String) = instrumentation.sendStatus(0, Bundle().apply {
            putString("homeDeviceLiveStage", stage)
            putLong("homeDeviceLiveElapsedRealtime", SystemClock.elapsedRealtime())
        })

        try {
            signal("read_live_device_status")
            val launched = ActivityScenario.launch(MainActivity::class.java)
            scenario = launched
            lateinit var viewModel: HomeDeviceViewModel
            launched.onActivity { activity ->
                // Dispose the ordinary home composition so this read cannot create a second
                // dashboard polling loop. The test never writes account/order storage.
                activity.setContent { }
                viewModel = ViewModelProvider(activity)[HomeDeviceViewModel::class.java]
                poll = scope.launch { viewModel.pollDeviceStatus() }
            }
            val response = try {
                runBlocking { withTimeout(15_000L) { viewModel.deviceStatus.filterNotNull().first() } }
            } finally {
                runBlocking { poll?.cancelAndJoin() }
            }
            report.put("deviceStatus", JSONObject().put("status", redact(response.status, response))
                .put("deviceSnIsBlank", response.deviceSn.isBlank()).put("statusColor", response.statusColor)
                .put("installUrl", safeUrl(response.installUrl)).put("previewUrl", safeUrl(response.previewUrl)))
            assertTrue("The live API did not return a usable installation URL",
                isHomeDevicePageUrl(response.installUrl))

            signal("read_live_installation_dom")
            launched.onActivity { activity ->
                activity.setContent {
                    LongCareTheme {
                        WebViewScreen(
                            actions = WebViewActions(onNavigateBack = { activity.finish() }),
                            url = response.installUrl,
                            title = context.getString(R.string.home_device_installation),
                            showNativeToolbar = false,
                        )
                    }
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            var lastSnapshot: JSONObject? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                val result = AtomicReference<JSONObject?>()
                launched.onActivity { activity ->
                    findWebView(activity.window.decorView)?.let { webView ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WebView.getCurrentWebViewPackage()?.let { renderer ->
                                report.put("rendererPackage", JSONObject().put("packageName", renderer.packageName)
                                    .put("versionName", renderer.versionName ?: ""))
                            }
                        }
                        val width = webView.width
                        val height = webView.height
                        val progress = webView.progress
                        webView.evaluateJavascript(DOM_SNAPSHOT_SCRIPT) { raw ->
                            // Never persist raw Javascript output: it may contain server-provided
                            // text with an embedded URL, query value or device identifier.
                            val decoded = JSONTokener(raw).nextValue() as? String
                            val dom = decoded?.let(::JSONObject) ?: JSONObject()
                            dom.put("title", redact(dom.optString("title"), response))
                            dom.put("bodyTextPreview", redact(dom.optString("bodyTextPreview"), response))
                            dom.put("webViewWidth", width).put("webViewHeight", height)
                                .put("webViewProgress", progress).put("elapsedRealtime", SystemClock.elapsedRealtime())
                            result.set(dom)
                        }
                    }
                }
                val callbackDeadline = minOf(deadline, SystemClock.elapsedRealtime() + 2_000L)
                while (result.get() == null && SystemClock.elapsedRealtime() < callbackDeadline) SystemClock.sleep(25L)
                result.get()?.let { snapshot ->
                    snapshots.put(snapshot)
                    lastSnapshot = snapshot
                }
                if (lastSnapshot?.let(::hasVisibleDocumentContent) == true) break
                SystemClock.sleep(250L)
            }
            report.put("domSnapshots", snapshots)
            saveScreenshot(directory)
            val final = requireNotNull(lastSnapshot) { "The live WebView returned no DOM snapshot within 15 seconds" }
            assertTrue("The live WebView has zero native dimensions",
                final.optInt("webViewWidth") > 0 && final.optInt("webViewHeight") > 0)
            assertTrue("The live H5 document did not finish loading within 15 seconds",
                final.optString("readyState") == "complete" && final.optInt("webViewProgress") == 100)
            assertTrue("The live H5 document finished loading but its body text is empty; inspect sanitized DOM geometry",
                final.optInt("bodyTextLength") > 0)
            assertTrue("The live H5 has text but html/body/#app has no visible geometry; this is a blank-page failure",
                hasVisibleDocumentGeometry(final))
            report.put("success", true)
            signal("completed")
        } catch (error: Throwable) {
            failure = error
            report.put("failure", error.javaClass.simpleName)
            report.put("domSnapshots", snapshots)
            saveScreenshot(directory)
            throw error
        } finally {
            runBlocking { poll?.cancelAndJoin() }
            scope.cancel()
            try {
                scenario?.close()
            } finally {
                // Relaunch normal product content after the temporary setContent diagnostic.
                // No logout, clear-data, preferences or navigation state is written by the test.
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                report.put("success", failure == null && report.optBoolean("success"))
                File(directory, "home-device-live-integration.json").writeText(report.toString(2))
            }
        }
    }

    private fun saveScreenshot(directory: File) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "home-device-live-installation.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun safeUrl(value: String): JSONObject = Uri.parse(value).let {
        JSONObject().put("scheme", it.scheme ?: "").put("host", it.host ?: "").put("path", it.path ?: "")
    }

    private fun hasVisibleDocumentContent(snapshot: JSONObject): Boolean =
        snapshot.optString("readyState") == "complete" && snapshot.optInt("webViewProgress") == 100 &&
            snapshot.optInt("webViewWidth") > 0 && snapshot.optInt("webViewHeight") > 0 &&
            snapshot.optInt("bodyTextLength") > 0 && hasVisibleDocumentGeometry(snapshot)

    private fun hasVisibleDocumentGeometry(snapshot: JSONObject): Boolean {
        val viewportWidth = snapshot.optDouble("innerWidth", 0.0)
        val viewportHeight = snapshot.optDouble("innerHeight", 0.0)
        if (viewportWidth <= 0.0 || viewportHeight <= 0.0) return false
        return listOf("documentElement" to "htmlStyle", "body" to "bodyStyle", "app" to "appStyle").all { (element, styleKey) ->
            val rect = snapshot.optJSONObject("${element}Rect") ?: return@all false
            val width = rect.optDouble("width", 0.0)
            val height = rect.optDouble("height", 0.0)
            val x = rect.optDouble("x", 0.0)
            val y = rect.optDouble("y", 0.0)
            val style = snapshot.optJSONObject(styleKey) ?: return@all false
            width > 0.0 && height > 0.0 && x < viewportWidth && y < viewportHeight &&
                x + width > 0.0 && y + height > 0.0 && style.optString("display") != "none" &&
                style.optString("visibility") !in listOf("hidden", "collapse") &&
                style.optString("opacity", "1").toDoubleOrNull()?.let { it > 0.0 } == true
        }
    }

    private fun redact(text: String, model: ZhiChiDeviceStatusModel): String {
        var safe = text
        listOf(model.installUrl, model.previewUrl).forEach { url ->
            val uri = Uri.parse(url)
            // Query values are used only in memory to remove accidental echoes from DOM text.
            if (uri.isHierarchical) uri.queryParameterNames.forEach { name ->
                uri.getQueryParameters(name).filter(String::isNotBlank).forEach { value ->
                    safe = safe.replace(value, "<redacted>").replace(Uri.encode(value), "<redacted>")
                }
            }
        }
        if (model.deviceSn.isNotBlank()) safe = safe.replace(model.deviceSn, "<redacted-device>")
        safe = Regex("(?:https?|wss?)://[^\\s<>\"']+").replace(safe) { match ->
            val uri = Uri.parse(match.value)
            "${uri.scheme}://${uri.host.orEmpty()}${uri.path.orEmpty()}"
        }
        return Regex("(?i)(token|authorization|signature)\\s*[:=]\\s*[^\\s,;]+").replace(safe, "$1=<redacted>")
    }

    private companion object {
        // This reads only the rendered document and layout; no cookies, storage or JS app state.
        val DOM_SNAPSHOT_SCRIPT = """
            (function() {
              function rect(el) {
                if (!el) return null;
                var r = el.getBoundingClientRect();
                return {x:r.x, y:r.y, width:r.width, height:r.height};
              }
              function style(el) {
                if (!el) return null;
                var s = window.getComputedStyle(el);
                return {display:s.display, visibility:s.visibility, opacity:s.opacity,
                  position:s.position, width:s.width, height:s.height, minHeight:s.minHeight,
                  overflow:s.overflow, overflowX:s.overflowX, overflowY:s.overflowY};
              }
              function tree(el, depth) {
                if (!el) return null;
                return {tag:el.tagName, rect:rect(el), style:style(el), childCount:el.children.length,
                  children:depth > 0 ? Array.prototype.slice.call(el.children,0,4).map(function(child) {
                    return tree(child,depth-1);
                  }) : []};
              }
              function supportsHeight(value) {
                return !!(window.CSS && typeof window.CSS.supports === 'function' && window.CSS.supports('height',value));
              }
              var body = document.body;
              var app = document.getElementById('app');
              var text = body ? (body.innerText || '').trim() : '';
              var viewport = window.visualViewport;
              return JSON.stringify({
                readyState:document.readyState,
                title:document.title,
                userAgent:navigator.userAgent,
                innerWidth:window.innerWidth,
                innerHeight:window.innerHeight,
                devicePixelRatio:window.devicePixelRatio,
                documentElementClientHeight:document.documentElement.clientHeight,
                visualViewport:viewport ? {width:viewport.width, height:viewport.height,
                  offsetLeft:viewport.offsetLeft, offsetTop:viewport.offsetTop,
                  pageLeft:viewport.pageLeft, pageTop:viewport.pageTop, scale:viewport.scale} : null,
                cssHeightSupport:{dvh:supportsHeight('100dvh'), vh:supportsHeight('100vh'), svh:supportsHeight('100svh')},
                bodyTextLength:text.length,
                bodyTextPreview:text.slice(0,200),
                bodyRect:rect(body),
                documentElementRect:rect(document.documentElement),
                appRect:rect(app),
                htmlStyle:style(document.documentElement),
                bodyStyle:style(body),
                appStyle:style(app),
                appTree:tree(app,2)
              });
            })()
        """.trimIndent()
    }
}
