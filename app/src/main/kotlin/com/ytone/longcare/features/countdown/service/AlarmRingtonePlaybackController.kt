package com.ytone.longcare.features.countdown.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics.ClockSample
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** All MediaPlayer calls, including data-source IPC and release, belong to one playback Looper. */
internal class AlarmRingtonePlaybackController(
    private val context: Context,
    workerHandler: Handler? = null,
    private val callbackHandler: Handler = Handler(Looper.getMainLooper()),
    private val playerFactory: () -> MediaPlayer = { MediaPlayer() },
    private val mediaSource: () -> Uri? = {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    },
    private val vibratorProvider: () -> Vibrator? = { context.getSystemService<Vibrator>() },
) {
    internal sealed interface PlaybackEvent {
        data class Started(
            val startedAtElapsedMs: Long,
            val startupDurationMs: Long,
            val nativeStartedAt: ClockSample? = null,
        ) : PlaybackEvent
        data class Failed(val outcome: String, val failureType: String, val releaseSucceeded: Boolean) : PlaybackEvent
        data object Finished : PlaybackEvent
    }

    private val worker = workerHandler?.let(::PlaybackWorker) ?: SharedWorker.worker
    private val current = AtomicReference<Request?>(null)

    val isPlaying: Boolean get() = current.get()?.state?.get() == State.PLAYING
    val isAudibleFallbackSafe: Boolean get() = !worker.mayBePlaying

    /** Evidence belongs to the same request token as playback; never reuse a replacement's clocks. */
    fun preparedAt(token: Long): ClockSample? =
        current.get()?.takeIf { it.id == token }?.preparedAt?.get()

    data class TimingSnapshot(val preparedAt: ClockSample?, val nativeStartedAt: ClockSample?)

    /** Call before stop() clears the request; no worker calls or playback authorization occur. */
    fun timingSnapshot(): TimingSnapshot? = current.get()?.let {
        TimingSnapshot(it.preparedAt.get(), it.nativeStartedAt.get())
    }

    fun prepare(
        preparationDeadlineElapsedMs: Long,
        playbackDeadlineElapsedMs: Long,
        isCurrent: () -> Boolean,
        onPrepared: (Long) -> Unit,
        onEvent: (PlaybackEvent) -> Unit,
    ) {
        val request = Request(
            id = nextId.incrementAndGet(), context = context, callbackHandler = callbackHandler,
            playerFactory = playerFactory, mediaSource = mediaSource, vibratorProvider = vibratorProvider,
            preparationDeadline = preparationDeadlineElapsedMs, playbackDeadline = playbackDeadlineElapsedMs,
            isCurrent = isCurrent, callbacks = AtomicReference(Callbacks(onPrepared, onEvent)),
        )
        current.getAndSet(request)?.let(worker::cancel)
        worker.submit(request)
    }

    /** Only the main-thread prepared callback may authorize this exact request to play. */
    fun play(token: Long): Boolean =
        current.get()?.takeIf { it.id == token }?.let(worker::play) ?: false

    /** Non-blocking even while a provider/native call owns the playback thread. */
    fun stop() {
        current.getAndSet(null)?.let(worker::cancel)
    }

    private enum class State { PREPARING, PREPARED, AUTHORIZED, STARTING, PLAYING, FINISHED, CANCELLED }
    private data class Callbacks(val prepared: (Long) -> Unit, val event: (PlaybackEvent) -> Unit)
    private class Request(
        val id: Long,
        val context: Context,
        val callbackHandler: Handler,
        val playerFactory: () -> MediaPlayer,
        val mediaSource: () -> Uri?,
        val vibratorProvider: () -> Vibrator?,
        val preparationDeadline: Long,
        val playbackDeadline: Long,
        val isCurrent: () -> Boolean,
        val callbacks: AtomicReference<Callbacks?>,
        val createdAt: Long = SystemClock.elapsedRealtime(),
        val state: AtomicReference<State> = AtomicReference(State.PREPARING),
        val preparedAt: AtomicReference<ClockSample?> = AtomicReference(null),
        val nativeStartedAt: AtomicReference<ClockSample?> = AtomicReference(null),
    )
    private class Session(val request: Request, val player: MediaPlayer) {
        var vibrator: Vibrator? = null
        var released = false
    }

    private class PlaybackWorker(private val handler: Handler) {
        private val desired = AtomicReference<Request?>(null)
        // Worker-owned state; no lock is ever held across a platform call.
        private var active: Session? = null
        private var quarantined = false
        @Volatile var mayBePlaying = false
            private set
        private val drain = Runnable { drain() }

        fun submit(request: Request) {
            desired.getAndSet(request)?.let(::invalidate)
            scheduleDrain()
        }

        fun cancel(request: Request) {
            invalidate(request)
            if (desired.compareAndSet(request, null)) scheduleDrain()
        }

        private fun invalidate(request: Request) {
            request.state.set(State.CANCELLED)
            request.callbacks.set(null)
            request.callbackHandler.removeCallbacksAndMessages(request)
            handler.removeCallbacksAndMessages(request)
        }

        private fun scheduleDrain() {
            // At most one pending drain, even across repeated Service recreation while IPC hangs.
            handler.removeCallbacks(drain)
            handler.post(drain)
        }

        private fun current(request: Request): Boolean =
            desired.get() === request && request.state.get() != State.CANCELLED && request.isCurrent()

        private fun drain() {
            val request = desired.get()
            if (active?.request !== request) active?.let(::release)
            if (request == null || !current(request) || active != null) return
            if (quarantined) {
                post(request) { it.event(PlaybackEvent.Failed("playback_prepare_failed", "PlaybackWorkerUnavailable", false)) }
                return
            }
            if (SystemClock.elapsedRealtime() >= request.preparationDeadline) return
            var session: Session? = null
            try {
                session = Session(request, request.playerFactory())
                active = session
                val owned = session
                if (!current(request) || SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                    release(owned)
                    return
                }
                owned.player.setOnPreparedListener {
                    if (owned.released || request.state.get() != State.PREPARING) return@setOnPreparedListener
                    if (!current(request) || SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                        release(owned)
                    } else if (request.state.compareAndSet(State.PREPARING, State.PREPARED)) {
                        request.preparedAt.set(ClockSample.capture())
                        post(request) {
                            // A queued prepared callback must not erase a later terminal error.
                            if (request.state.get() == State.PREPARED) {
                                if (SystemClock.elapsedRealtime() < request.preparationDeadline) it.prepared(request.id)
                                else expirePrepared(request)
                            }
                        }
                    }
                }
                owned.player.setOnErrorListener { _, what, extra ->
                    if (!owned.released) {
                        val outcome = when (request.state.get()) {
                            State.PLAYING -> "playback_failed"
                            State.AUTHORIZED, State.STARTING -> "playback_start_failed"
                            else -> "playback_prepare_failed"
                        }
                        fail(owned, outcome, "MediaPlayerError(what=$what,extra=$extra)")
                    }
                    true
                }
                owned.player.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                val source = requireNotNull(request.mediaSource())
                if (!current(request) || SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                    release(owned)
                    return
                }
                owned.player.setDataSource(request.context, source)
                if (!current(request) || SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                    release(owned)
                    return
                }
                owned.player.isLooping = true
                owned.player.setVolume(1f, 1f)
                if (!current(request) || SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                    release(owned)
                    return
                }
                owned.player.prepareAsync()
            } catch (error: Exception) {
                val released = session?.let(::release) ?: true
                post(request) { it.event(PlaybackEvent.Failed("playback_prepare_failed", error.javaClass.simpleName, released)) }
            }
        }

        private fun expirePrepared(request: Request) {
            // Do not overwrite FINISHED and erase a concurrently queued terminal error.
            if (request.state.compareAndSet(State.PREPARED, State.CANCELLED)) cancel(request)
        }

        fun play(request: Request): Boolean {
            if (!current(request)) {
                cancel(request)
                return false
            }
            if (SystemClock.elapsedRealtime() >= request.preparationDeadline) {
                expirePrepared(request)
                return false
            }
            // One authorization/queued play per prepared token, never an unbounded duplicate queue.
            if (!request.state.compareAndSet(State.PREPARED, State.AUTHORIZED)) return false
            // Token-tagged so cancellation removes a queued play rather than retaining its closure.
            return handler.postAtTime({
                val session = active?.takeIf { it.request === request && !it.released } ?: return@postAtTime
                if (request.state.get() != State.AUTHORIZED) return@postAtTime
                // Set before the final cancellation check: a concurrent timeout must not publish
                // another audible notification while native start may already have begun.
                mayBePlaying = true
                if (!current(request) || SystemClock.elapsedRealtime() >= request.playbackDeadline ||
                    !request.state.compareAndSet(State.AUTHORIZED, State.STARTING)) {
                    release(session)
                    return@postAtTime
                }
                try {
                    session.player.start()
                    // Native start returning is observable here; physical audio output is not.
                    val nativeStartedAt = ClockSample.capture()
                    request.nativeStartedAt.set(nativeStartedAt)
                    val startedAt = nativeStartedAt.elapsed
                    if (!current(request) || startedAt >= request.playbackDeadline) {
                        release(session)
                        return@postAtTime
                    }
                    request.state.set(State.PLAYING)
                    val deadline = minOf(request.playbackDeadline, startedAt + AlarmRingtoneService.RINGING_TIMEOUT_MS)
                    handler.postAtTime({
                        if (!session.released) {
                            release(session)
                            post(request) { it.event(PlaybackEvent.Finished) }
                        }
                    }, request, SystemClock.uptimeMillis() + (deadline - startedAt))
                    startVibration(session, deadline)
                    if (!current(request) || SystemClock.elapsedRealtime() >= deadline) {
                        release(session)
                    } else {
                        post(request) {
                            // The main Looper may deliver this after the worker already timed out.
                            if (request.state.get() == State.PLAYING && SystemClock.elapsedRealtime() < deadline) {
                                it.event(PlaybackEvent.Started(startedAt, startedAt - request.createdAt, nativeStartedAt))
                            }
                        }
                    }
                } catch (error: Exception) {
                    fail(session, "playback_start_failed", error.javaClass.simpleName)
                }
            }, request, SystemClock.uptimeMillis()).also { accepted ->
                if (!accepted) cancel(request)
            }
        }

        private fun fail(session: Session, outcome: String, failureType: String) {
            val released = release(session)
            post(session.request) { it.event(PlaybackEvent.Failed(outcome, failureType, released)) }
        }

        private fun post(request: Request, action: (Callbacks) -> Unit) {
            if (!current(request)) return
            request.callbackHandler.postAtTime({
                if (current(request)) request.callbacks.get()?.let(action)
            }, request, SystemClock.uptimeMillis())
        }

        private fun release(session: Session): Boolean {
            if (session.released) return !quarantined
            session.released = true
            session.request.state.updateAndGet { if (it == State.CANCELLED) it else State.FINISHED }
            if (active === session) active = null
            handler.removeCallbacksAndMessages(session.request)
            try { session.vibrator?.cancel() } catch (error: Exception) {
                logE("AlarmRingtoneService: 停止Vibrator失败", throwable = error)
            }
            session.vibrator = null
            return try {
                // release is valid in ANY MediaPlayer state. Never stop/reset/isPlaying in Preparing.
                // If native IPC is stuck, this runs only after it returns; never release concurrently.
                session.player.release()
                mayBePlaying = false
                true
            } catch (error: Exception) {
                quarantined = true // Do not accumulate more potentially unreleased native players.
                logE("AlarmRingtoneService: 释放MediaPlayer失败", throwable = error)
                false
            }
        }

        private fun startVibration(session: Session, deadline: Long) {
            try {
                if (!current(session.request)) return
                session.vibrator = session.request.vibratorProvider()
                if (!current(session.request) || SystemClock.elapsedRealtime() >= deadline) return
                val pattern = longArrayOf(0, 1000, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    session.vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    session.vibrator?.vibrate(pattern, 0)
                }
            } catch (error: Exception) {
                logE("AlarmRingtoneService: 初始化Vibrator失败", throwable = error)
            }
        }
    }

    private object SharedWorker {
        // One process-bounded worker: a wedged provider must not cause a new thread per attempt.
        val worker by lazy {
            PlaybackWorker(Handler(HandlerThread("LongCareAlarmPlayback").apply { start() }.looper))
        }
    }

    private companion object { val nextId = AtomicLong() }
}
