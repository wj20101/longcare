package com.ytone.longcare.features.maindashboard.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NfcTestDialogState {
    var showDialog by mutableStateOf(false)
        private set

    var nfcTagId by mutableStateOf("")
        private set

    fun show(tagId: String) {
        nfcTagId = tagId
        showDialog = true
    }

    fun dismiss() {
        showDialog = false
        nfcTagId = ""
    }
}
