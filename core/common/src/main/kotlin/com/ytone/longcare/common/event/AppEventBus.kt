package com.ytone.longcare.common.event

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    val events = _events.asSharedFlow()

    suspend fun send(event: AppEvent) {
        _events.emit(event)
    }
}

enum class ScanSource {
    SYSTEM_NFC,
    EXTERNAL_RFID,
}

sealed class AppEvent {
    data class NfcIntentReceived(val intent: Intent) : AppEvent()
    data class TagScanned(val tagId: String, val source: ScanSource) : AppEvent()
    data class ReaderConnectionChanged(
        val connected: Boolean,
        val source: ScanSource = ScanSource.EXTERNAL_RFID,
    ) : AppEvent()
    data class ReaderError(
        val message: String,
        val source: ScanSource = ScanSource.EXTERNAL_RFID,
    ) : AppEvent()
}
