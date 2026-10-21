package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AlarmRingtonePlaybackControllerTest {
    @After fun cleanup() = unmockkAll()

    @Test fun `failed stop still releases the player`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = mockk<MediaPlayer>(relaxed = true)
        every { player.isPlaying } returns true
        every { player.stop() } throws IllegalStateException("player state changed")
        val controller = AlarmRingtonePlaybackController(context)
        ReflectionHelpers.setField(controller, "mediaPlayer", player)
        controller.stop()
        verify(exactly = 1) { player.release() }
        assertFalse(controller.isPlaying)
    }

    @Test fun `partial player creation is released when preparation fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setDataSource(any<Context>(), any<android.net.Uri>()) } just runs
        every { anyConstructed<MediaPlayer>().setAudioAttributes(any()) } just runs
        every { anyConstructed<MediaPlayer>().isLooping = any() } just runs
        every { anyConstructed<MediaPlayer>().setVolume(any(), any()) } just runs
        every { anyConstructed<MediaPlayer>().prepare() } throws IOException("unavailable alarm sound")
        every { anyConstructed<MediaPlayer>().release() } just runs
        val controller = AlarmRingtonePlaybackController(context)
        assertFalse(controller.start())
        assertEquals("IOException", controller.lastStartFailureType)
        verify(exactly = 1) { anyConstructed<MediaPlayer>().release() }
        verify(exactly = 0) { anyConstructed<MediaPlayer>().start() }
    }
}
