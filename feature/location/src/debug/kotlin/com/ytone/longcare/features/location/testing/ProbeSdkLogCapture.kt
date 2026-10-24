package com.ytone.longcare.features.location.testing

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel

/** Only these literal SDK prefixes may cross the callback boundary. Never retain their suffixes. */
internal enum class ProbeSdkLogKind(val traceName: String, private val prefix: String) {
    NETWORK_RESULT_REPLACED("network_result_replaced", "i#netloc#net loc filter shake:"),
    INVALID_NETWORK_RESULT("invalid_network_result", "i#netloc#receive wrong net loc:"),
    LITTLE_SHAKE("little_shake", "i#locfilter#little shake:"),
    SDK_MAIN_RESULT("sdk_main_result", "i#alocmain#get main-loc callback:");

    companion object {
        fun parse(message: String?): ProbeSdkLogKind? =
            entries.firstOrNull { message?.startsWith(it.prefix) == true }
    }
}

internal data class ProbeSdkLogEvent(
    val kind: ProbeSdkLogKind,
    val receivedElapsedRealtime: Long,
    val receivedUptimeMillis: Long,
    val logWallMillis: Long,
)

/**
 * The SDK invokes callbacks synchronously on arbitrary SDK threads. This boundary only parses,
 * reads clocks and tries a bounded send. The Main (non-immediate) consumer owns trace I/O.
 */
internal class ProbeSdkLogCapture(
    capacity: Int = 64,
    private val elapsedRealtime: () -> Long,
    private val uptimeMillis: () -> Long,
) {
    val events = Channel<ProbeSdkLogEvent>(capacity)
    private val accepting = AtomicBoolean(true)
    private val inFlight = AtomicInteger()
    private val matched = AtomicLong()
    private val enqueued = AtomicLong()
    private val dropped = AtomicLong()
    private val afterClose = AtomicLong()
    private val callbackFailures = AtomicLong()
    private val consumed = AtomicLong()
    private val writeFailures = AtomicLong()

    init { require(capacity > 0 && capacity <= 256) }

    fun onLog(message: String?, logWallMillis: Long) {
        inFlight.incrementAndGet()
        try {
            val kind = ProbeSdkLogKind.parse(message) ?: return
            if (!accepting.get()) {
                afterClose.incrementAndGet()
                return
            }
            matched.incrementAndGet()
            val event = ProbeSdkLogEvent(kind, elapsedRealtime(), uptimeMillis(), logWallMillis)
            if (events.trySend(event).isSuccess) enqueued.incrementAndGet() else dropped.incrementAndGet()
        } catch (_: Throwable) {
            // No SDK text or Throwable may escape this diagnostic callback.
            callbackFailures.incrementAndGet()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    /** Marks the observation boundary before unregistering the process-global SDK callback. */
    fun close() {
        accepting.set(false)
        events.close()
    }

    fun onConsumed(written: Boolean) {
        consumed.incrementAndGet()
        if (!written) writeFailures.incrementAndGet()
    }

    fun snapshot(): Snapshot {
        // Read activity first: a callback finishing during the counter reads must not make
        // a mixture of old counters and a final zero inFlight look complete.
        val activeBefore = inFlight.get()
        return Snapshot(matched.get(), enqueued.get(), consumed.get(), dropped.get(), afterClose.get(),
            callbackFailures.get(), writeFailures.get(), maxOf(activeBefore, inFlight.get()), accepting.get())
    }

    internal data class Snapshot(
        val matched: Long,
        val enqueued: Long,
        val consumed: Long,
        val dropped: Long,
        val afterClose: Long,
        val callbackFailures: Long,
        val writeFailures: Long,
        val inFlight: Int,
        val accepting: Boolean,
    ) {
        val pending: Long get() = (enqueued - consumed).coerceAtLeast(0L)
        // A live/in-flight snapshot cannot certify completeness. New post-close callbacks are
        // outside the stated observation boundary; any already observed ones remain explicit.
        val complete: Boolean get() = !accepting && inFlight == 0 && pending == 0L &&
            dropped == 0L && callbackFailures == 0L && writeFailures == 0L &&
            matched == enqueued
    }
}
