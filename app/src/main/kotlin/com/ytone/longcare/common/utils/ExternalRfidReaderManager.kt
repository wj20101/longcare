package com.ytone.longcare.common.utils

import android.app.Activity

interface ExternalRfidReaderManager {
    fun start(activity: Activity)
    fun stop(activity: Activity)
    fun submitHidCandidate(rawPayload: String)
}
