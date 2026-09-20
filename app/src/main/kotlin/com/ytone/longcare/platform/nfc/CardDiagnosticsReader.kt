package com.ytone.longcare.platform.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import com.ytone.longcare.common.utils.NfcUtils
import java.util.concurrent.atomic.AtomicBoolean

/** One foreground read session. Late Binder callbacks are ignored after stop. */
internal class CardDiagnosticsReader(
    private val activity: Activity,
    private val adapter: NfcAdapter,
    private val onTag: (String?) -> Unit,
) {
    private val reading = AtomicBoolean(false)

    fun start() {
        if (reading.getAndSet(true)) return
        adapter.enableReaderMode(activity, { tag ->
            val id = tag.id?.takeIf { it.isNotEmpty() && it.size <= 64 }
                ?.let(NfcUtils::bytesToHexString)
            activity.runOnUiThread { if (reading.get()) onTag(id) }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    fun stop() {
        if (reading.getAndSet(false)) adapter.disableReaderMode(activity)
    }
}
