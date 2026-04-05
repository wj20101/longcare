package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcManager @Inject constructor(
    private val appEventBus: AppEventBus
) : DefaultLifecycleObserver {

    private var currentActivity: Activity? = null
    private var isNfcEnabled = false
    private var nfcEnableDialog: AlertDialog? = null

    fun enableNfcForActivity(activity: Activity) {
        logD("NfcManager", "enableNfcForActivity: ${activity::class.java.simpleName}")
        currentActivity = activity
        isNfcEnabled = true

        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
        }

        if (NfcForegroundDispatchDelegate.isResumed(activity)) {
            checkAndEnableNfc(activity)
        }
    }

    fun disableNfcForActivity(activity: Activity) {
        if (currentActivity != activity) return
        isNfcEnabled = false

        NfcForegroundDispatchDelegate.disableSafely(
            activity = activity,
            requireResumed = activity is LifecycleOwner
        )

        if (activity is LifecycleOwner) {
            activity.lifecycle.removeObserver(this)
        }
    }

    fun handleNfcIntent(activity: Activity, intent: Intent) {
        if (currentActivity != activity || !isNfcEnabled) return

        activity.intent = intent
        if (activity is LifecycleOwner) {
            activity.lifecycleScope.launch {
                appEventBus.send(AppEvent.NfcIntentReceived(intent))
            }
        }
        handleBuiltInTag(intent)
    }

    private fun handleBuiltInTag(intent: Intent) {
        val tag = NfcUtils.getTagFromIntent(intent) ?: return
        val tagId = NfcUtils.bytesToHexString(tag.id)
        if (tagId.isBlank()) return

        currentActivity?.takeIf { isNfcEnabled }?.let { activity ->
            if (activity is LifecycleOwner) {
                activity.lifecycleScope.launch {
                    appEventBus.send(AppEvent.TagScanned(tagId, ScanSource.SYSTEM_NFC))
                }
            }
        }
    }

    private fun checkAndEnableNfc(activity: Activity) {
        if (!NfcForegroundDispatchDelegate.isResumed(activity)) return

        when {
            !NfcUtils.isNfcSupported(activity) -> {
                nfcEnableDialog = NfcEnableDialogDelegate.dismiss(nfcEnableDialog)
            }

            !NfcUtils.isNfcEnabled(activity) -> {
                nfcEnableDialog = NfcEnableDialogDelegate.showIfNeeded(activity, nfcEnableDialog)
            }

            else -> {
                nfcEnableDialog = NfcEnableDialogDelegate.dismiss(nfcEnableDialog)
                NfcUtils.enableForegroundDispatch(activity)
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentActivity?.let { activity ->
            isNfcEnabled = true
            checkAndEnableNfc(activity)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentActivity?.takeIf { isNfcEnabled }?.let { activity ->
            NfcForegroundDispatchDelegate.disableSafely(
                activity = activity,
                requireResumed = false
            )
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        nfcEnableDialog = NfcEnableDialogDelegate.dismiss(nfcEnableDialog)
        isNfcEnabled = false
        currentActivity = null
    }
}
