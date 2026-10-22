package com.ytone.longcare.features.countdown.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController.PlaybackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real platform player and Loopers, but silent local PCM and no vibrator, Service or alarm. */
@RunWith(AndroidJUnit4::class)
class AlarmRingtonePlaybackIntegrationTest {
    @Test
    fun silent_pcm_prepares_asynchronously_and_starts_on_the_playback_worker() = Fixture().use { fixture ->
        fixture.prepare(playWhenPrepared = true)
        fixture.awaitMainHeartbeat()
        val event = fixture.nextEvent()
        assertTrue("Expected real native playback to start, got $event", event is PlaybackEvent.Started)
        val started = event as PlaybackEvent.Started
        assertTrue("The prepared token must have been authorized before native start", fixture.playAccepted.get())
        assertTrue(started.startupDurationMs >= 0L)
        assertTrue(started.startedAtElapsedMs > 0L)
        val player = fixture.player()
        assertEquals(1, player.asyncPreparations.get())
        assertEquals(0, player.synchronousPreparations.get())
        assertEquals(1, player.starts.get())
        assertTrue(fixture.controller.isPlaying)
        fixture.assertMainCallbacks()

        fixture.stopAndDrain()
        assertEquals(1, player.releases.get())
        assertFalse(fixture.controller.isPlaying)
        fixture.assertWorkerCalls(player)
        assertTrue("Unexpected duplicate playback outcome", fixture.events.isEmpty())
    }

    @Test
    fun stopping_a_real_prepared_player_releases_it_and_rejects_its_late_play_token() = Fixture().use { fixture ->
        fixture.prepare(playWhenPrepared = false)
        fixture.awaitMainHeartbeat()
        val token = fixture.preparedToken()
        val player = fixture.player()
        assertEquals(1, player.asyncPreparations.get())
        assertEquals(0, player.synchronousPreparations.get())
        assertEquals(0, player.starts.get())
        assertFalse("Prepared is not playing", fixture.controller.isPlaying)
        fixture.assertMainCallbacks()

        var stalePlayAccepted = true
        fixture.onMain {
            fixture.controller.stop()
            stalePlayAccepted = fixture.controller.play(token)
        }
        assertFalse("A stopped request cannot authorize a late play token", stalePlayAccepted)
        fixture.drainWorker()
        fixture.stopAndDrain() // Idempotent cleanup must not release the native player twice.
        assertEquals(1, player.releases.get())
        assertEquals(0, player.starts.get())
        assertFalse(fixture.controller.isPlaying)
        fixture.assertWorkerCalls(player)
        assertTrue("A cancelled token must not publish a playback outcome", fixture.events.isEmpty())
    }

    private class Fixture : Closeable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        private val silentAudio = createSilentPcm(context)
        private val worker = HandlerThread("SilentAlarmPlaybackTest").apply { start() }
        private val workerHandler = Handler(worker.looper)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val players = LinkedBlockingQueue<RecordingPlayer>()
        private val prepared = LinkedBlockingQueue<Long>()
        private val callbacks = CopyOnWriteArrayList<Thread>()
        private val heartbeat = CountDownLatch(1)
        val playAccepted = AtomicBoolean(false)
        val events = LinkedBlockingQueue<PlaybackEvent>()
        val controller = AlarmRingtonePlaybackController(
            context = context,
            workerHandler = workerHandler,
            callbackHandler = mainHandler,
            playerFactory = { RecordingPlayer().also(players::add) },
            mediaSource = { Uri.fromFile(silentAudio) },
            vibratorProvider = { null },
        )

        fun prepare(playWhenPrepared: Boolean) = onMain {
            val now = SystemClock.elapsedRealtime()
            controller.prepare(
                preparationDeadlineElapsedMs = now + WAIT_SECONDS * 1_000L,
                playbackDeadlineElapsedMs = now + 30_000L,
                isCurrent = { true },
                onPrepared = { token ->
                    callbacks.add(Thread.currentThread())
                    prepared.add(token)
                    if (playWhenPrepared) playAccepted.set(controller.play(token))
                },
                onEvent = { event ->
                    callbacks.add(Thread.currentThread())
                    events.add(event)
                },
            )
            // A real main-Looper message must run; no wall-clock performance threshold is asserted.
            mainHandler.post { heartbeat.countDown() }
        }

        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }

        fun awaitMainHeartbeat() = await(heartbeat, "Main Looper did not process the heartbeat")

        fun nextEvent(): PlaybackEvent = requireNotNull(events.poll(WAIT_SECONDS, TimeUnit.SECONDS)) {
            "No native playback result within the preparation deadline"
        }

        fun preparedToken(): Long = requireNotNull(prepared.poll(WAIT_SECONDS, TimeUnit.SECONDS)) {
            "Real MediaPlayer did not deliver onPrepared"
        }

        fun player(): RecordingPlayer = requireNotNull(players.poll(WAIT_SECONDS, TimeUnit.SECONDS)) {
            "No native MediaPlayer was constructed"
        }

        fun assertMainCallbacks() {
            assertFalse("No controller callbacks observed", callbacks.isEmpty())
            assertTrue(callbacks.all { it === Looper.getMainLooper().thread })
        }

        fun assertWorkerCalls(player: RecordingPlayer) {
            assertNotSame(Looper.getMainLooper().thread, worker)
            assertTrue("Native player access escaped its owner thread: ${player.calls}",
                player.calls.all { it.second === worker })
            assertTrue(player.calls.any { it.first == "setDataSource" })
            assertTrue(player.calls.any { it.first == "release" })
        }

        fun drainWorker() {
            val drained = CountDownLatch(1)
            assertTrue(workerHandler.post { drained.countDown() })
            await(drained, "Playback worker did not finish queued cleanup")
            instrumentation.waitForIdleSync()
        }

        fun stopAndDrain() {
            onMain(controller::stop)
            drainWorker()
        }

        override fun close() {
            try {
                stopAndDrain()
            } finally {
                worker.quitSafely()
                worker.join(5_000L) // Only the test-owned thread; never joined from the app main Looper.
                silentAudio.delete()
            }
            assertFalse("Test playback thread leaked", worker.isAlive)
        }
    }

    /** Delegate every observed operation to Android's real MediaPlayer, not a mock or shadow. */
    private class RecordingPlayer : MediaPlayer() {
        val asyncPreparations = AtomicInteger()
        val synchronousPreparations = AtomicInteger()
        val starts = AtomicInteger()
        val releases = AtomicInteger()
        val calls = CopyOnWriteArrayList<Pair<String, Thread>>().apply {
            add("create" to Thread.currentThread())
        }

        override fun setOnPreparedListener(listener: MediaPlayer.OnPreparedListener?) {
            calls.add("setOnPreparedListener" to Thread.currentThread())
            super.setOnPreparedListener(listener?.let { original ->
                MediaPlayer.OnPreparedListener { player ->
                    calls.add("onPrepared" to Thread.currentThread())
                    original.onPrepared(player)
                }
            })
        }

        override fun setOnErrorListener(listener: MediaPlayer.OnErrorListener?) {
            calls.add("setOnErrorListener" to Thread.currentThread())
            super.setOnErrorListener(listener)
        }

        override fun setAudioAttributes(attributes: AudioAttributes) {
            calls.add("setAudioAttributes" to Thread.currentThread())
            super.setAudioAttributes(attributes)
        }

        override fun setLooping(looping: Boolean) {
            calls.add("setLooping" to Thread.currentThread())
            super.setLooping(looping)
        }

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            calls.add("setVolume" to Thread.currentThread())
            super.setVolume(leftVolume, rightVolume)
        }

        override fun setDataSource(context: Context, uri: Uri) {
            calls.add("setDataSource" to Thread.currentThread())
            super.setDataSource(context, uri)
        }

        override fun prepareAsync() {
            calls.add("prepareAsync" to Thread.currentThread())
            asyncPreparations.incrementAndGet()
            super.prepareAsync()
        }

        override fun prepare() {
            calls.add("prepare" to Thread.currentThread())
            synchronousPreparations.incrementAndGet()
            super.prepare()
        }

        override fun start() {
            calls.add("start" to Thread.currentThread())
            super.start()
            starts.incrementAndGet()
        }

        override fun release() {
            calls.add("release" to Thread.currentThread())
            super.release()
            releases.incrementAndGet()
        }
    }

    private companion object {
        const val WAIT_SECONDS = 15L

        fun await(latch: CountDownLatch, message: String) {
            assertTrue(message, latch.await(WAIT_SECONDS, TimeUnit.SECONDS))
        }

        fun createSilentPcm(context: Context): File {
            val sampleRate = 16_000
            val pcmBytes = sampleRate * 2 // One second, mono PCM16; zero samples are silence.
            val bytes = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcmBytes)
                .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
                .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort(2).putShort(16)
                .put("data".toByteArray(Charsets.US_ASCII)).putInt(pcmBytes)
                .array()
            return File.createTempFile("silent-alarm-", ".wav", context.cacheDir).apply { writeBytes(bytes) }
        }
    }
}
