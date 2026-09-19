package com.ytone.longcare.assistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.google.mlkit.common.MlKit

/** Separate process/sandbox; no QLZ, tracking, update worker or analytics startup. */
@HiltAndroidApp
class AssistantApplication : Application() {
    private var mlKitInitialized = false

    fun initializeAfterConsent() {
        if (!mlKitInitialized) {
            MlKit.initialize(this)
            mlKitInitialized = true
        }
    }
}
