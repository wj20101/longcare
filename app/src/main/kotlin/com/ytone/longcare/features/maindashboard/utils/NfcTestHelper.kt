package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch
import com.ytone.longcare.R

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

    fun dismissDialog() {
        logD(NfcTestConfig.TEST_TAG, "关闭弹窗")
        dialogState.dismiss()
    }

    @Composable
    fun NfcTagDialog() {
        val clipboard = LocalClipboard.current
        val clipboardLabel = stringResource(R.string.nfc_clipboard_label)
        val coroutineScope = rememberCoroutineScope()
        RenderNfcTestTagDialog(
            showDialog = dialogState.showDialog,
            nfcTagId = dialogState.nfcTagId,
            onCopy = {
                coroutineScope.launch {
                    copyNfcTagIdAndDismiss(
                        tagId = dialogState.nfcTagId,
                        writeClipboardEntry = { text ->
                            logD(NfcTestConfig.TEST_TAG, "复制Tag ID: $text")
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(clipboardLabel, text))
                            )
                        },
                        onCopySuccess = { toastHelper.showShort(R.string.nfc_copy_success) },
                        onCopyFailure = { toastHelper.showShort(R.string.nfc_copy_failed) },
                        dismissDialog = ::dismissDialog
                    )
                }
            },
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
        // A resumed singleTop Activity is paused before Android delivers onNewIntent.
        // Keep the logical listener active across that boundary so the NFC intent is not dropped.
        // NfcManager still disables foreground dispatch from its own onPause callback.
        logD(NfcTestConfig.TEST_TAG, "页面暂停，保留NFC事件监听等待onNewIntent")
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        onNfcTestHelperDestroy(
            currentActivity = currentActivity,
            disable = ::disable
        )
    }
}
