package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.debug.NfcTestConfig
import kotlinx.coroutines.Job

internal class NfcTestListeningDelegate(
    private val appEventBus: AppEventBus,
    private val toastHelper: ToastHelper,
    private val nfcManager: NfcManager
) {
    private var eventJob: Job? = null
    var isListening: Boolean = false
        private set

    fun start(
        activity: Activity,
        isEnabled: Boolean,
        onTagDetected: (String) -> Unit
    ) {
        logD(NfcTestConfig.TEST_TAG, "开始NFC监听 - isEnabled: $isEnabled, isListening: $isListening")
        if (!isEnabled || isListening) {
            logD(NfcTestConfig.TEST_TAG, "跳过NFC监听 - isEnabled: $isEnabled, isListening: $isListening")
            return
        }
        if (!ensureNfcAvailableForTest(activity, toastHelper)) return

        try {
            logD(NfcTestConfig.TEST_TAG, "通过NfcManager启用NFC功能")
            nfcManager.enableNfcForActivity(activity)
            isListening = true

            eventJob?.cancel()
            if (activity is LifecycleOwner) {
                logD(NfcTestConfig.TEST_TAG, "开始监听AppEventBus事件")
                eventJob = startNfcIntentCollection(
                    activity = activity,
                    appEventBus = appEventBus,
                    isListening = { isListening },
                    onTagDetected = onTagDetected,
                    toastHelper = toastHelper
                )
                logD(NfcTestConfig.TEST_TAG, "事件监听协程已设置完成")
            } else {
                logD(NfcTestConfig.TEST_TAG, "Activity不是LifecycleOwner，无法监听事件")
            }
        } catch (e: Exception) {
            logD(NfcTestConfig.TEST_TAG, "启动NFC监听失败: ${e.message}")
            toastHelper.showShort("启动NFC监听失败: ${e.message}")
        }
    }

    fun stop(activity: Activity) {
        if (!isListening) return
        try {
            eventJob?.cancel()
            eventJob = null
            logD(NfcTestConfig.TEST_TAG, "通过NfcManager禁用NFC功能")
            nfcManager.disableNfcForActivity(activity)
            isListening = false
            logD(NfcTestConfig.TEST_TAG, "NFC监听已停止")
        } catch (e: Exception) {
            logD(NfcTestConfig.TEST_TAG, "停止NFC监听异常: ${e.message}")
        }
    }
}
