package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.debug.NfcTestEntrySession
import com.ytone.longcare.debug.NfcTestConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcTestHelper @Inject constructor(
    private val appEventBus: AppEventBus,
    private val toastHelper: ToastHelper,
    nfcManager: com.ytone.longcare.common.utils.NfcManager
) : DefaultLifecycleObserver {

    private var currentActivity: Activity? = null
    private var isEnabled = false
    private val dialogState = NfcTestDialogState()
    private val listeningDelegate = NfcTestListeningDelegate(
        appEventBus = appEventBus,
        toastHelper = toastHelper,
        nfcManager = nfcManager
    )

    fun enable(activity: Activity) {
        if (!NfcTestEntrySession.isEnabled()) {
            logD(NfcTestConfig.TEST_TAG, "NFC测试功能已禁用")
            return
        }

        logD(NfcTestConfig.TEST_TAG, "启用NFC测试功能")
        currentActivity = activity
        isEnabled = true

        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                startNfcListening(activity)
            }
        }
    }

    fun disable(activity: Activity) {
        if (currentActivity == activity) {
            isEnabled = false
            stopNfcListening(activity)

            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }

            dismissDialog()
            currentActivity = null
        }
    }

    private fun startNfcListening(activity: Activity) {
        listeningDelegate.start(
            activity = activity,
            isEnabled = isEnabled,
            onTagDetected = { tagId ->
                dialogState.show(tagId)
                logD(
                    NfcTestConfig.TEST_TAG,
                    "已设置弹窗显示: showDialog=${dialogState.showDialog}, nfcTagId=${dialogState.nfcTagId}"
                )
            }
        )
    }

    private fun stopNfcListening(activity: Activity) {
        listeningDelegate.stop(activity)
    }

    private fun copyToClipboard(text: String) {
        currentActivity?.let { activity ->
            copyNfcTagIdToClipboard(activity, text, toastHelper)
        }
    }

    fun dismissDialog() {
        logD(NfcTestConfig.TEST_TAG, "关闭弹窗")
        dialogState.dismiss()
    }

    fun copyAndDismiss() {
        logD(NfcTestConfig.TEST_TAG, "复制Tag ID: ${dialogState.nfcTagId}")
        copyToClipboard(dialogState.nfcTagId)
        dismissDialog()
    }

    @Composable
    fun NfcTagDialog() {
        RenderNfcTestTagDialog(
            showDialog = dialogState.showDialog,
            nfcTagId = dialogState.nfcTagId,
            onCopy = ::copyAndDismiss,
            onDismiss = ::dismissDialog
        )
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        onNfcTestHelperResume(
            isEnabled = isEnabled,
            isListening = listeningDelegate.isListening,
            currentActivity = currentActivity,
            startListening = ::startNfcListening
        )
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        onNfcTestHelperPause(
            isListening = listeningDelegate.isListening,
            currentActivity = currentActivity,
            stopListening = ::stopNfcListening
        )
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        onNfcTestHelperDestroy(
            currentActivity = currentActivity,
            disable = ::disable
        )
    }
}
