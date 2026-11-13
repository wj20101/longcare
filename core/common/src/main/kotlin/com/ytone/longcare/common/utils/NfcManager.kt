package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcManager @Inject constructor(
    private val appEventBus: AppEventBus
) : DefaultLifecycleObserver {

    private var currentActivity: Activity? = null
    private var scanSessionId: String? = null
    private var tagDelivery: Job? = null
    private var isNfcEnabled = false
    private var nfcEnableDialog: AlertDialog? = null

    fun enableNfcForActivity(activity: Activity, sessionId: String) {
        currentActivity?.let { previous ->
            scanSessionId?.let { disableNfcForActivity(previous, it) }
        }
        logD("NfcManager", "enableNfcForActivity: ${activity::class.java.simpleName}")
        currentActivity = activity
        scanSessionId = sessionId
        isNfcEnabled = true

        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
        }

        if (NfcForegroundDispatchDelegate.isResumed(activity)) {
            checkAndEnableNfc(activity)
        }
    }

    fun disableNfcForActivity(activity: Activity, sessionId: String) {
        if (currentActivity != activity || scanSessionId != sessionId) return
        isNfcEnabled = false
        scanSessionId = null
        tagDelivery?.cancel()
        tagDelivery = null

        NfcForegroundDispatchDelegate.disableSafely(
            activity = activity,
            requireResumed = activity is LifecycleOwner
        )

        if (activity is LifecycleOwner) {
            activity.lifecycle.removeObserver(this)
        }
        nfcEnableDialog = NfcEnableDialogDelegate.dismiss(nfcEnableDialog)
        currentActivity = null
    }

    fun handleNfcIntent(activity: Activity, intent: Intent, dispatchAllowed: Boolean = true) {
        if (!NfcIntentActions.isSupportedTagAction(intent.action)) return
        val sessionId = scanSessionId
        val tagId = try {
            NfcUtils.bytesToHexString(NfcUtils.getTagFromIntent(intent)?.id)
        } catch (_: RuntimeException) {
            ""
        } finally {
            // Consume the framework's Intent as well as Activity.getIntent(), even with no
            // active order. Neither recreation nor entering a later page may reuse this card.
            intent.action = Intent.ACTION_MAIN
            intent.removeExtra(NfcAdapter.EXTRA_TAG)
            intent.removeExtra(NfcAdapter.EXTRA_ID)
            intent.removeExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            activity.intent = intent
        }
        if (!dispatchAllowed || sessionId == null || tagId.isBlank() ||
            currentActivity != activity || !isNfcEnabled || activity !is LifecycleOwner) return

        tagDelivery?.cancel()
        tagDelivery = activity.lifecycleScope.launch {
            // onNewIntent normally arrives after onPause and before onResume. STARTED
            // also waits for a fresh background-to-foreground Intent without a timer.
            activity.lifecycle.currentStateFlow.first {
                it.isAtLeast(Lifecycle.State.STARTED) || it == Lifecycle.State.DESTROYED
            }
            if (currentActivity == activity && scanSessionId == sessionId && isNfcEnabled &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                appEventBus.send(AppEvent.TagScanned(tagId, ScanSource.SYSTEM_NFC, sessionId))
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
        if (owner != currentActivity) return
        currentActivity?.let { activity ->
            isNfcEnabled = true
            checkAndEnableNfc(activity)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        if (owner != currentActivity) return
        currentActivity?.takeIf { isNfcEnabled }?.let { activity ->
            NfcForegroundDispatchDelegate.disableSafely(
                activity = activity,
                requireResumed = false
            )
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        if (owner != currentActivity) return
        nfcEnableDialog = NfcEnableDialogDelegate.dismiss(nfcEnableDialog)
        isNfcEnabled = false
        scanSessionId = null
        tagDelivery?.cancel()
        tagDelivery = null
        currentActivity = null
    }
}
