package com.ytone.longcare.features.location.testing

import org.junit.Assert.*
import org.junit.Test

class ProbeSdkLogCaptureTest {
    @Test fun `only exact whitelisted prefixes produce typed metadata without the suffix`() {
        val prefixes = mapOf(
            "i#netloc#net loc filter shake:" to ProbeSdkLogKind.NETWORK_RESULT_REPLACED,
            "i#netloc#receive wrong net loc:" to ProbeSdkLogKind.INVALID_NETWORK_RESULT,
            "i#locfilter#little shake:" to ProbeSdkLogKind.LITTLE_SHAKE,
            "i#alocmain#get main-loc callback:" to ProbeSdkLogKind.SDK_MAIN_RESULT,
        )
        val secretSuffix = " 999.123456,888.654321 ssid=private key=secret utc=987654321"
        val capture = ProbeSdkLogCapture(elapsedRealtime = { 12L }, uptimeMillis = { 9L })
        prefixes.forEach { (prefix, kind) ->
            capture.onLog(prefix + secretSuffix, 123L)
            val event = capture.events.tryReceive().getOrThrow()
            assertEquals(ProbeSdkLogEvent(kind, 12L, 9L, 123L), event)
            assertFalse(event.toString().contains(secretSuffix))
            assertFalse(event.toString().contains("999.123456"))
            assertFalse(event.toString().contains("secret"))
            capture.onConsumed(true)
        }
        capture.close()
        assertTrue(capture.snapshot().complete)
    }

    @Test fun `wrong levels tags separators casing and embedded prefixes are ignored`() {
        val capture = ProbeSdkLogCapture(elapsedRealtime = { error("must not read clock") }, uptimeMillis = { 0L })
        listOf(null, "", "i#locfilter#little shake", "I#locfilter#little shake:",
            "d#locfilter#little shake:", "i#netloc#little shake:",
            " i#locfilter#little shake:", "prefix i#locfilter#little shake:",
            "i#locfilter#little shakeX:").forEach { capture.onLog(it, 0L) }
        capture.close()
        assertTrue(capture.events.tryReceive().isFailure)
        assertEquals(0L, capture.snapshot().matched)
        assertTrue(capture.snapshot().complete)
    }

    @Test fun `bounded queue reports overflow pending and post close rejection`() {
        val capture = ProbeSdkLogCapture(capacity = 2, elapsedRealtime = { 12L }, uptimeMillis = { 9L })
        repeat(3) { capture.onLog("i#locfilter#little shake:ignored", 123L) }
        capture.close()
        capture.onLog("i#locfilter#little shake:ignored", 124L)
        val pending = capture.snapshot()
        assertEquals(3L, pending.matched)
        assertEquals(2L, pending.enqueued)
        assertEquals(2L, pending.pending)
        assertEquals(1L, pending.dropped)
        assertEquals(1L, pending.afterClose)
        assertFalse(pending.complete)
        repeat(2) { capture.events.tryReceive().getOrThrow(); capture.onConsumed(true) }
        assertTrue(capture.events.tryReceive().isFailure)
        assertEquals(0L, capture.snapshot().pending)
        assertFalse(capture.snapshot().complete)
    }

    @Test fun `callback and writer failures prevent completeness without escaping`() {
        val failedClock = ProbeSdkLogCapture(elapsedRealtime = { error("private data") }, uptimeMillis = { 0L })
        failedClock.onLog("i#locfilter#little shake:private data", 0L)
        failedClock.close()
        assertEquals(1L, failedClock.snapshot().callbackFailures)
        assertFalse(failedClock.snapshot().complete)
        val failedWrite = ProbeSdkLogCapture(elapsedRealtime = { 1L }, uptimeMillis = { 1L })
        failedWrite.onLog("i#locfilter#little shake:", 0L)
        failedWrite.events.tryReceive().getOrThrow()
        failedWrite.onConsumed(false)
        failedWrite.close()
        assertEquals(1L, failedWrite.snapshot().writeFailures)
        assertFalse(failedWrite.snapshot().complete)
    }
}
