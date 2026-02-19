package com.ytone.longcare.common.utils

import android.app.Activity
import androidx.appcompat.app.AlertDialog

internal object NfcEnableDialogDelegate {

    fun showIfNeeded(activity: Activity, currentDialog: AlertDialog?): AlertDialog? {
        if (currentDialog?.isShowing == true) return currentDialog
        return NfcUtils.showEnableNfcDialog(
            activity,
            title = "NFC未开启",
            message = "请在设置中开启NFC功能以使用签到功能"
        )
    }

    fun dismiss(currentDialog: AlertDialog?): AlertDialog? {
        currentDialog?.takeIf { it.isShowing }?.dismiss()
        return null
    }
}
