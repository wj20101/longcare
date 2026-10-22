package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController.PlaybackEvent
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class AlarmRingtonePlaybackControllerTest {
    private val controllers = mutableListOf<AlarmRingtonePlaybackController>()
    @After fun cleanup() {
        controllers.forEach { it.stop() }
        idle()
        unmockkAll()
    }

    @Test fun `preparation is not playback and prepared authorization starts exactly once`() {
        val f = fixture()
        f.prepare()
        idle()
        assertFalse(f.controller.isPlaying)
        assertTrue(f.events.isEmpty())
        verify(exactly = 1) { f.player.prepareAsync() }
        verify(exactly = 0) { f.player.prepare() }
        verify(exactly = 0) { f.player.start() }
        f.prepared()
        assertFalse(f.controller.isPlaying)
        assertTrue(f.events.isEmpty())
        assertTrue(f.controller.play(f.tokens.single()))
        assertFalse(f.controller.play(f.tokens.single()))
        idle()
        assertTrue(f.controller.isPlaying)
        assertTrue(f.events.single() is PlaybackEvent.Started)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        f.prepared() // Duplicate prepared after the original preparation deadline cannot stop playing.
        assertTrue(f.controller.isPlaying)
        assertEquals(1, f.tokens.size)
        verify(exactly = 0) { f.player.release() }
        verify(exactly = 1) { f.player.start() }
    }

    @Test fun `cancellation in Preparing releases without stop reset or isPlaying and ignores late callback`() {
        val f = fixture()
        f.prepare(); idle()
        f.controller.stop(); idle()
        f.prepared()
        assertTrue(f.tokens.isEmpty())
        assertTrue(f.events.isEmpty())
        assertFalse(f.controller.isPlaying)
        verify(exactly = 1) { f.player.release() }
        verify(exactly = 0) { f.player.stop() }
        verify(exactly = 0) { f.player.reset() }
        verify(exactly = 0) { f.player.isPlaying }
        verify(exactly = 0) { f.player.start() }
    }

    @Test fun `replacement ignores old prepared and error callbacks without releasing the new player`() {
        val old = mockk<MediaPlayer>(relaxed = true)
        val new = mockk<MediaPlayer>(relaxed = true)
        val oldPrepared = slot<MediaPlayer.OnPreparedListener>()
        val oldError = slot<MediaPlayer.OnErrorListener>()
        every { old.setOnPreparedListener(capture(oldPrepared)) } just runs
        every { old.setOnErrorListener(capture(oldError)) } just runs
        val f = fixture(player = new, playerFactory = { if (!oldPrepared.isCaptured) old else new })
        f.prepare(); idle()
        f.prepare(); idle()
        oldPrepared.captured.onPrepared(old)
        oldError.captured.onError(old, 1, -1004)
        idle()
        f.prepared()
        f.controller.play(f.tokens.single()); idle()
        verify(exactly = 1) { old.release() }
        verify(exactly = 0) { old.start() }
        verify(exactly = 0) { new.release() }
        verify(exactly = 1) { new.start() }
        assertTrue(f.events.single() is PlaybackEvent.Started)
    }

    @Test fun `ownership loss after main authorization prevents worker start`() {
        val f = fixture()
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single())
        f.owned = false
        idle()
        verify(exactly = 0) { f.player.start() }
        verify(exactly = 1) { f.player.release() }
        assertTrue(f.events.isEmpty())
    }

    @Test fun `expired preparation callback releases even before a main timeout is dispatched`() {
        val f = fixture()
        f.prepare(); idle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        f.prepared()
        assertTrue(f.tokens.isEmpty())
        verify(exactly = 1) { f.player.release() }
        verify(exactly = 0) { f.player.start() }
    }

    @Test fun `ownership recheck consuming the preparation budget cannot authorize a late start`() {
        val f = fixture()
        f.prepare(); idle(); f.prepared()
        f.ownershipCheck = { ShadowSystemClock.advanceBy(Duration.ofSeconds(6)); true }
        assertFalse(f.controller.play(f.tokens.single()))
        idle()
        verify(exactly = 0) { f.player.start() }
        verify(exactly = 1) { f.player.release() }
    }

    @Test fun `expired overall deadline rejects an already authorized queued play`() {
        val f = fixture()
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(36))
        idle()
        verify(exactly = 0) { f.player.start() }
        verify(exactly = 1) { f.player.release() }
        assertTrue(f.events.isEmpty())
    }

    @Test fun `cancelled queued work never creates a player`() {
        val creations = AtomicInteger()
        val player = mockk<MediaPlayer>(relaxed = true)
        val f = fixture(player, playerFactory = { creations.incrementAndGet(); player })
        f.prepare()
        f.controller.stop()
        idle()
        assertEquals(0, creations.get())
        verify(exactly = 0) { f.player.setDataSource(any<Context>(), any<Uri>()) }
        assertTrue(f.events.isEmpty())
    }

    @Test fun `data source and asynchronous preparation errors release once with accurate diagnostics`() {
        val f = fixture()
        every { f.player.setDataSource(any<Context>(), any<Uri>()) } throws IOException("source unavailable")
        f.prepare(); idle()
        assertEquals(PlaybackEvent.Failed("playback_prepare_failed", "IOException", true), f.events.single())
        verify(exactly = 1) { f.player.release() }
        verify(exactly = 0) { f.player.prepareAsync() }

        val async = fixture()
        async.prepare(); idle()
        async.error.captured.onError(async.player, 1, -1004)
        async.error.captured.onError(async.player, 1, -1004)
        idle()
        assertEquals(PlaybackEvent.Failed("playback_prepare_failed", "MediaPlayerError(what=1,extra=-1004)", true), async.events.single())
        verify(exactly = 1) { async.player.release() }
    }

    @Test fun `queued prepared callback cannot erase a subsequent terminal preparation error`() {
        val f = fixture()
        f.prepare(); idle()
        f.listener.captured.onPrepared(f.player)
        f.error.captured.onError(f.player, 1, -1004)
        idle()
        assertTrue(f.tokens.isEmpty())
        assertEquals(PlaybackEvent.Failed("playback_prepare_failed", "MediaPlayerError(what=1,extra=-1004)", true), f.events.single())
        verify(exactly = 0) { f.player.start() }
        verify(exactly = 1) { f.player.release() }
    }

    @Test fun `native start exception and later playback error are distinct failures`() {
        val f = fixture()
        every { f.player.start() } throws IllegalStateException("start failed")
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single()); idle()
        assertEquals(PlaybackEvent.Failed("playback_start_failed", "IllegalStateException", true), f.events.single())
        assertFalse(f.controller.isPlaying)

        val playing = fixture()
        playing.prepare(); idle(); playing.prepared()
        playing.controller.play(playing.tokens.single()); idle()
        playing.error.captured.onError(playing.player, 100, 0); idle()
        assertEquals("playback_failed", (playing.events.last() as PlaybackEvent.Failed).outcome)
        verify(exactly = 1) { playing.player.release() }
    }

    @Test fun `worker playback deadline releases without waiting for service cleanup`() {
        val f = fixture()
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single()); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertFalse(f.controller.isPlaying)
        assertEquals(PlaybackEvent.Finished, f.events.last())
        verify(exactly = 1) { f.player.release() }
    }

    @Test fun `release failure is idempotent quarantines worker and cannot authorize audible fallback`() {
        val f = fixture()
        every { f.player.release() } throws IllegalStateException("native release failed")
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single()); idle()
        f.controller.stop(); idle()
        f.controller.stop(); idle()
        assertFalse(f.controller.isAudibleFallbackSafe)
        f.prepare(); idle()
        assertEquals(PlaybackEvent.Failed("playback_prepare_failed", "PlaybackWorkerUnavailable", false), f.events.last())
        verify(exactly = 1) { f.player.release() }
        verify(exactly = 1) { f.player.prepareAsync() }
    }

    @Test
    @Config(sdk = [28, 34])
    fun `alarm media preserves alarm audio policy without regulatory audibility enforcement`() {
        val f = fixture()
        val attributes = slot<AudioAttributes>()
        every { f.player.setAudioAttributes(capture(attributes)) } just runs
        f.prepare(); idle()
        assertEquals(AudioAttributes.USAGE_ALARM, attributes.captured.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.captured.contentType)
        assertEquals(0, attributes.captured.flags and AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
    }

    @Test fun `expiry while obtaining vibrator cannot start late vibration or report stale playback`() {
        val vibrator = mockk<Vibrator>(relaxed = true)
        val f = fixture(vibratorProvider = {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(36))
            vibrator
        })
        f.prepare(); idle(); f.prepared()
        f.controller.play(f.tokens.single()); idle()
        verify(exactly = 0) { vibrator.vibrate(any<VibrationEffect>()) }
        verify(exactly = 1) { vibrator.cancel() }
        verify(exactly = 1) { f.player.release() }
        assertFalse(f.controller.isPlaying)
        assertTrue(f.events.isEmpty())
    }

    @Test fun `replacements behind blocked source coalesce and never initialize canceled requests`() {
        val thread = HandlerThread("coalesced-alarm-test").apply { start() }
        val handler = Handler(thread.looper)
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val prepared = CountDownLatch(1)
        val creations = AtomicInteger()
        val player = mockk<MediaPlayer>(relaxed = true)
        every { player.setDataSource(any<Context>(), any<Uri>()) } answers {
            if (creations.get() == 1) {
                entered.countDown()
                check(unblock.await(10, TimeUnit.SECONDS))
            }
        }
        every { player.prepareAsync() } answers { prepared.countDown() }
        val f = fixture(player, workerHandler = handler, playerFactory = {
            creations.incrementAndGet()
            player
        })
        try {
            f.prepare()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(100) { f.prepare(); f.controller.stop() }
            f.prepare()
            unblock.countDown()
            assertTrue(prepared.await(5, TimeUnit.SECONDS))
            assertEquals("Only the blocked original and latest request create players", 2, creations.get())
            verify(exactly = 1) { player.prepareAsync() }
            verify(exactly = 0) { player.start() }
            assertTrue(f.events.isEmpty())
        } finally {
            unblock.countDown()
            f.controller.stop()
            thread.quitSafely()
            thread.join(5_000)
            assertFalse("test-owned worker terminated", thread.isAlive)
        }
    }

    @Test fun `blocked data source never blocks main cancellation and releases only after worker returns`() {
        val thread = HandlerThread("blocked-alarm-test").apply { start() }
        val handler = Handler(thread.looper)
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val released = CountDownLatch(1)
        val player = mockk<MediaPlayer>(relaxed = true)
        val calls = mutableListOf<Looper?>()
        every { player.setDataSource(any<Context>(), any<Uri>()) } answers {
            calls += Looper.myLooper()
            entered.countDown()
            check(unblock.await(10, TimeUnit.SECONDS))
        }
        every { player.release() } answers {
            calls += Looper.myLooper()
            released.countDown()
        }
        val f = fixture(player, workerHandler = handler, playerFactory = {
            calls += Looper.myLooper()
            player
        })
        try {
            f.prepare()
            assertTrue("worker entered data source", entered.await(5, TimeUnit.SECONDS))
            f.controller.stop()
            // This main-Looper task can run while setDataSource remains blocked; no release race.
            var heartbeat = false
            Handler(Looper.getMainLooper()).post { heartbeat = true }
            idle()
            assertTrue(heartbeat)
            assertEquals(1L, released.count)
            unblock.countDown()
            assertTrue("release after native return", released.await(5, TimeUnit.SECONDS))
            assertTrue(calls.all { it === thread.looper })
            verify(exactly = 0) { player.prepareAsync() }
            verify(exactly = 0) { player.start() }
            assertTrue(f.events.isEmpty())
        } finally {
            unblock.countDown()
            f.controller.stop()
            thread.quitSafely()
            thread.join(5_000)
            assertFalse("test-owned worker terminated", thread.isAlive)
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun fixture(
        player: MediaPlayer = mockk(relaxed = true),
        workerHandler: Handler = Handler(Looper.getMainLooper()),
        playerFactory: () -> MediaPlayer = { player },
        vibratorProvider: () -> Vibrator? = { null },
    ): Fixture {
        val prepared = slot<MediaPlayer.OnPreparedListener>()
        val error = slot<MediaPlayer.OnErrorListener>()
        every { player.setOnPreparedListener(capture(prepared)) } just runs
        every { player.setOnErrorListener(capture(error)) } just runs
        val controller = AlarmRingtonePlaybackController(
            ApplicationProvider.getApplicationContext(), workerHandler = workerHandler,
            playerFactory = playerFactory, mediaSource = { Uri.parse("content://test/alarm") },
            vibratorProvider = vibratorProvider,
        )
        controllers += controller
        return Fixture(controller, player, prepared, error)
    }

    private inner class Fixture(
        val controller: AlarmRingtonePlaybackController,
        val player: MediaPlayer,
        val listener: CapturingSlot<MediaPlayer.OnPreparedListener>,
        val error: CapturingSlot<MediaPlayer.OnErrorListener>,
    ) {
        var owned = true
        var ownershipCheck: (() -> Boolean)? = null
        val tokens = mutableListOf<Long>()
        val events = mutableListOf<PlaybackEvent>()
        fun prepare() = controller.prepare(SystemClock.elapsedRealtime() + 5_000,
            SystemClock.elapsedRealtime() + 35_000, { ownershipCheck?.invoke() ?: owned }, { tokens += it }, { events += it })
        fun prepared() { listener.captured.onPrepared(player); idle() }
    }
}
