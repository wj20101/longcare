package com.ytone.longcare.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NfcTestEntrySession {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    fun toggle(): Boolean {
        val next = !_enabled.value
        _enabled.value = next
        return next
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun resetForTest() {
        _enabled.value = false
    }
}
