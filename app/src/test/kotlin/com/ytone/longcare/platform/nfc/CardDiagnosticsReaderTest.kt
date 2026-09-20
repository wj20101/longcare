package com.ytone.longcare.platform.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class CardDiagnosticsReaderTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val adapter = mockk<NfcAdapter>(relaxed = true)
    private val callback = slot<NfcAdapter.ReaderCallback>()
    private val values = mutableListOf<String?>()
    private val reader = CardDiagnosticsReader(activity, adapter, values::add)

    private fun start() {
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }
        every { adapter.enableReaderMode(activity, capture(callback), any(), null) } just Runs
        reader.start()
    }
    private fun tag(id: ByteArray): Tag = mockk { every { this@mockk.id } returns id }

    @Test fun repeatedStartAndStopAreIdempotent() {
        start()
        reader.start()
        reader.stop()
        reader.stop()
        verify(exactly = 1) { adapter.enableReaderMode(activity, any(), any(), null) }
        verify(exactly = 1) { adapter.disableReaderMode(activity) }
    }

    @Test fun readsLocallyAndDiscardsCallbackAfterExit() {
        start()
        callback.captured.onTagDiscovered(tag(byteArrayOf(1, 2, 3, 4)))
        assertEquals(listOf("01020304"), values)
        reader.stop()
        callback.captured.onTagDiscovered(tag(byteArrayOf(5, 6, 7, 8)))
        assertEquals(listOf("01020304"), values)
    }

    @Test fun queuedCallbackCannotUpdateDisposedPage() {
        start()
        val queued = slot<Runnable>()
        every { activity.runOnUiThread(capture(queued)) } just Runs
        callback.captured.onTagDiscovered(tag(byteArrayOf(1)))
        reader.stop()
        queued.captured.run()
        assertTrue(values.isEmpty())
    }

    @Test fun malformedTagsProduceRecoverableEmptyResult() {
        start()
        callback.captured.onTagDiscovered(tag(byteArrayOf()))
        callback.captured.onTagDiscovered(tag(ByteArray(65)))
        assertEquals(listOf(null, null), values)
    }
}
