package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.debug.NfcTestConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private fun nfcTestLog(message: String) {
    NfcTestConfig.logD(message, tag = NfcTestConfig.TEST_TAG)
}

internal fun ensureNfcAvailableForTest(
    activity: Activity,
    toastHelper: ToastHelper
): Boolean {
    return when {
        !NfcUtils.isNfcSupported(activity) -> {
            nfcTestLog("设备不支持NFC")
            toastHelper.showShort("设备不支持NFC功能")
            false
        }
        !NfcUtils.isNfcEnabled(activity) -> {
            nfcTestLog("NFC未开启")
            toastHelper.showShort("请先开启NFC功能")
            false
        }
        else -> true
    }
}

internal fun startNfcIntentCollection(
    activity: LifecycleOwner,
    appEventBus: AppEventBus,
    isListening: () -> Boolean,
    onTagDetected: (String) -> Unit,
    toastHelper: ToastHelper
): Job {
    return activity.lifecycleScope.launch {
        nfcTestLog("事件监听协程已启动")
        appEventBus.events.collect { event ->
            nfcTestLog("收到事件: ${event::class.java.simpleName}, isListening: ${isListening()}")
            if (event is AppEvent.NfcIntentReceived && isListening()) {
                nfcTestLog("处理NFC事件: ${event.intent.action}")
                handleNfcTestIntent(event.intent, toastHelper, onTagDetected)
            } else if (event is AppEvent.NfcIntentReceived) {
                nfcTestLog("收到NFC事件但isListening为false")
            }
        }
    }
}

internal fun handleNfcTestIntent(
    intent: android.content.Intent,
    toastHelper: ToastHelper,
    onTagDetected: (String) -> Unit
) {
    try {
        nfcTestLog("处理NFC Intent: ${intent.action}")
        val tag = NfcUtils.getTagFromIntent(intent)
        if (tag != null) {
            val tagId = NfcUtils.bytesToHexString(tag.id)
            nfcTestLog("获取到Tag ID: $tagId")
            if (tagId.isNotEmpty()) {
                onTagDetected(tagId)
            } else {
                toastHelper.showShort("无法获取NFC标签ID")
            }
        } else {
            nfcTestLog("无法从 Intent 中获取 Tag")
            toastHelper.showShort("无法读取NFC标签")
        }
    } catch (e: Exception) {
        nfcTestLog("处理NFC数据失败: ${e.message}")
        toastHelper.showShort("处理NFC数据失败: ${e.message}")
    }
}

internal fun copyNfcTagIdToClipboard(
    activity: Activity,
    text: String,
    toastHelper: ToastHelper
) {
    try {
        val clipboardManager =
            activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("NFC Tag ID", text)
        clipboardManager.setPrimaryClip(clipData)
        nfcTestLog("已复制到剪贴板: $text")
        toastHelper.showShort("已复制到剪贴板")
    } catch (e: Exception) {
        nfcTestLog("复制到剪贴板失败: ${e.message}")
        toastHelper.showShort("复制失败")
    }
}

@Composable
internal fun renderNfcTestTagDialog(
    showDialog: Boolean,
    nfcTagId: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        NfcTagDialogContent(
            nfcTagId = nfcTagId,
            onCopy = onCopy,
            onDismiss = onDismiss
        )
    }
}

internal fun onNfcTestHelperResume(
    isEnabled: Boolean,
    isListening: Boolean,
    currentActivity: Activity?,
    startListening: (Activity) -> Unit
) {
    if (isEnabled && !isListening) {
        nfcTestLog("页面恢复，启用NFC监听")
        currentActivity?.let(startListening)
    }
}

internal fun onNfcTestHelperPause(
    isListening: Boolean,
    currentActivity: Activity?,
    stopListening: (Activity) -> Unit
) {
    if (isListening) {
        nfcTestLog("页面暂停，关闭NFC监听")
        currentActivity?.let(stopListening)
    }
}

internal fun onNfcTestHelperDestroy(
    currentActivity: Activity?,
    disable: (Activity) -> Unit
) {
    nfcTestLog("页面销毁，禁用NFC测试")
    currentActivity?.let(disable)
}
