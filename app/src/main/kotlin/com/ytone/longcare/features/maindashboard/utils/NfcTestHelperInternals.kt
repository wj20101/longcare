package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.debug.NfcTestConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.ytone.longcare.R

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
            toastHelper.showShort(R.string.nfc_not_supported)
            false
        }
        !NfcUtils.isNfcEnabled(activity) -> {
            nfcTestLog("NFC未开启")
            toastHelper.showShort(R.string.nfc_enable_required)
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
                toastHelper.showShort(R.string.nfc_tag_id_unavailable)
            }
        } else {
            nfcTestLog("无法从 Intent 中获取 Tag")
            toastHelper.showShort(R.string.nfc_tag_unreadable)
        }
    } catch (e: Exception) {
        nfcTestLog("处理NFC数据失败: ${e.message}")
        toastHelper.showShort(
            R.string.nfc_data_processing_failed,
            e.message.orEmpty(),
        )
    }
}

internal suspend fun copyNfcTagIdAndDismiss(
    tagId: String,
    writeClipboardEntry: suspend (String) -> Unit,
    onCopySuccess: () -> Unit,
    onCopyFailure: () -> Unit,
    dismissDialog: () -> Unit
): Boolean {
    return try {
        writeClipboardEntry(tagId)
        nfcTestLog("已复制到剪贴板: $tagId")
        onCopySuccess()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        nfcTestLog("复制到剪贴板失败: ${e.message}")
        onCopyFailure()
        false
    } finally {
        dismissDialog()
    }
}

@Composable
internal fun RenderNfcTestTagDialog(
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
