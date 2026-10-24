package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController.PlaybackEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class AlarmRingtonePlaybackDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player = mockk<MediaPlayer>(relaxed = true)
    private val prepared = slot<MediaPlayer.OnPreparedListener>()
    private val tokens = mutableListOf<Long>()
    private val events = mutableListOf<PlaybackEvent>()
    private val controller = AlarmRingtonePlaybackController(
        context, workerHandler = Handler(Looper.getMainLooper()), playerFactory = { player },
        mediaSource = { Uri.parse("content://test/alarm") }, vibratorProvider = { null },
    )

    @After fun cleanup() {
        controller.stop()
        shadowOf(Looper.getMainLooper()).idle()
        unmockkAll()
    }

    @Test fun `prepared clocks are isolated from cancelled and replacement tokens`() {
        prepare()
        prepared.captured.onPrepared(player)
        shadowOf(Looper.getMainLooper()).idle()
        val oldToken = tokens.single()
        assertNotNull(controller.preparedAt(oldToken))

        controller.stop()
        assertNull(controller.preparedAt(oldToken))
        assertNull(controller.timingSnapshot())
        prepare()

        assertNull(controller.preparedAt(oldToken))
        assertNull(controller.timingSnapshot()?.preparedAt)
        assertNull(controller.timingSnapshot()?.nativeStartedAt)
    }

    @Test fun `native start return clocks travel with the started event and remain available for timeout diagnostics`() {
        every { player.start() } answers { ShadowSystemClock.advanceBy(Duration.ofMillis(50)) }
        prepare()
        prepared.captured.onPrepared(player)
        shadowOf(Looper.getMainLooper()).idle()
        controller.play(tokens.single())
        shadowOf(Looper.getMainLooper()).idle()

        val event = events.single() as PlaybackEvent.Started
        val at = requireNotNull(event.nativeStartedAt)
        assertEquals(event.startedAtElapsedMs, at.elapsed)
        assertEquals(at, controller.timingSnapshot()?.nativeStartedAt)
        assertEquals(50L, at.elapsed - requireNotNull(controller.preparedAt(tokens.single())).elapsed)
    }

    private fun prepare() {
        every { player.setOnPreparedListener(capture(prepared)) } just runs
        val now = SystemClock.elapsedRealtime()
        controller.prepare(now + 5_000L, now + 35_000L, { true }, tokens::add, events::add)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
