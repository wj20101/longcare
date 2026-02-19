package com.ytone.longcare.common.utils

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

internal object NfcForegroundDispatchDelegate {

    fun isResumed(activity: Activity): Boolean {
        return (activity as? LifecycleOwner)
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED)
            ?: true
    }

    fun disableSafely(activity: Activity, requireResumed: Boolean) {
        if (requireResumed && !isResumed(activity)) return
        try {
            NfcUtils.disableForegroundDispatch(activity)
        } catch (_: IllegalStateException) {
            // Ignore: Activity may have already left resumed state.
        }
    }
}
