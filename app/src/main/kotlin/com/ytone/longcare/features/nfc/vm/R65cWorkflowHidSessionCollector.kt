package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.features.nfc.ui.R65cWorkflowHidCapturedKeyEvent

internal class R65cWorkflowHidSessionCollector {
    private val buffer = StringBuilder()

    fun onKeyEvent(event: R65cWorkflowHidCapturedKeyEvent): String? {
        return if (event.displayChar == "\\n") {
            drainPending()
        } else {
            if (event.displayChar.isNotEmpty()) {
                buffer.append(event.displayChar)
            }
            null
        }
    }

    fun hasPendingInput(): Boolean = buffer.isNotEmpty()

    fun drainPending(): String? {
        if (buffer.isEmpty()) return null
        return buffer.toString().also { buffer.clear() }
    }

    fun reset() {
        buffer.clear()
    }
}
