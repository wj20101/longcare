package com.ytone.longcare.common.utils

import android.app.Activity
import androidx.appcompat.app.AlertDialog

internal object NfcEnableDialogDelegate {

    fun showIfNeeded(activity: Activity, currentDialog: AlertDialog?): AlertDialog? {
        if (currentDialog?.isShowing == true) return currentDialog
        return NfcUtils.showEnableNfcDialog(activity)
    }

    fun dismiss(currentDialog: AlertDialog?): AlertDialog? {
        currentDialog?.takeIf { it.isShowing }?.dismiss()
        return null
    }
}
