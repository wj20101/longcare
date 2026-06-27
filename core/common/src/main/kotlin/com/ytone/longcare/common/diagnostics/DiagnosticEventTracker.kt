package com.ytone.longcare.common.diagnostics

import android.os.Build
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import java.net.URI

/**
 * Lightweight Bugly event wrapper for user-visible failures.
 *
 * Callers must pass only safe troubleshooting context, such as stage names,
 * order ids, status codes, file sizes, and SDK error codes. Do not pass photos,
 * base64 payloads, names, identity numbers, tokens, or full URLs.
 */
object DiagnosticEventTracker {
    private const val TAG = "DiagnosticEventTracker"
    private const val MAX_VALUE_LENGTH = 300

    fun trackEvent(
        category: String,
        event: String,
        description: String,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        report(
            category = category,
            event = event,
            description = description,
            isError = false,
            throwable = null,
            extras = extras,
        )
    }

    fun trackError(
        category: String,
        event: String,
        description: String,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        report(
            category = category,
            event = event,
            description = description,
            isError = true,
            throwable = throwable,
            extras = extras,
        )
    }

    fun safeUrlExtras(url: String): Map<String, Any?> {
        return try {
            val uri = URI(url)
            mapOf(
                "urlScheme" to uri.scheme,
                "urlHost" to uri.host,
                "urlPathLength" to (uri.rawPath?.length ?: 0),
            )
        } catch (_: Exception) {
            mapOf(
                "urlValid" to false,
                "urlLength" to url.length,
            )
        }
    }

    private fun report(
        category: String,
        event: String,
        description: String,
        isError: Boolean,
        throwable: Throwable?,
        extras: Map<String, Any?>,
    ) {
        try {
            val eventInfo = buildEventInfo(
                category = category,
                event = event,
                description = description,
                throwable = throwable,
                extras = extras,
            )
            if (isError) {
                safeLogError("$TAG: $description - $eventInfo", throwable)
            } else {
                safeLogInfo("$TAG: $description - $eventInfo")
            }
            CrashReport.postCatchedException(
                DiagnosticTrackingException(
                    category = category,
                    event = event,
                    message = eventInfo,
                    cause = throwable,
                ),
            )
        } catch (t: Throwable) {
            safeLogError("$TAG: 上报诊断事件失败 - ${t.message}", null)
        }
    }

    private fun safeLogInfo(message: String) {
        runCatching { logI(message) }
    }

    private fun safeLogError(message: String, throwable: Throwable?) {
        runCatching {
            if (throwable == null) {
                logE(message)
            } else {
                logE(message, throwable = throwable)
            }
        }
    }

    private fun buildEventInfo(
        category: String,
        event: String,
        description: String,
        throwable: Throwable?,
        extras: Map<String, Any?>,
    ): String {
        return buildString {
            appendLine("【$description】")
            appendLine("分类: $category")
            appendLine("事件码: $event")
            appendLine("时间戳: ${System.currentTimeMillis()}")
            appendLine("--- 设备信息 ---")
            appendLine("SDK版本: ${Build.VERSION.SDK_INT}")
            appendLine("厂商: ${Build.MANUFACTURER}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("品牌: ${Build.BRAND}")
            appendLine("可用堆内存: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
            if (extras.isNotEmpty()) {
                appendLine("--- 额外信息 ---")
                extras.forEach { (key, value) ->
                    appendLine("$key: ${value.safeValue()}")
                }
            }
            if (throwable != null) {
                appendLine("--- 异常信息 ---")
                appendLine("异常类型: ${throwable.javaClass.simpleName}")
                appendLine("异常消息: ${throwable.message.safeValue()}")
            }
        }
    }

    private fun Any?.safeValue(): String {
        val value = this?.toString().orEmpty()
        return if (value.length <= MAX_VALUE_LENGTH) {
            value
        } else {
            value.take(MAX_VALUE_LENGTH) + "...(truncated)"
        }
    }

    class DiagnosticTrackingException(
        val category: String,
        val event: String,
        message: String,
        cause: Throwable? = null,
    ) : Exception("[Diagnostic:$category:$event] $message", cause)
}
