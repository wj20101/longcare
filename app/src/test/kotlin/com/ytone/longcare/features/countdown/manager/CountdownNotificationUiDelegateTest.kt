package com.ytone.longcare.features.countdown.manager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownNotificationUiDelegateTest {
    @Test
    @Config(sdk = [24, 28, 31, 34, 36])
    fun `only fallback has alarm sound while foreground notification remains silent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delegate = CountdownNotificationUiDelegate(context, "countdown_test", 3002, 3003)
        delegate.createNotificationChannel()
        val normal = delegate.buildCountdownCompletionNotification(OrderKey(123L, 4), "护理", "1:2:3", 61_000L)
        val fallback = delegate.buildCountdownFallbackNotification(OrderKey(123L, 4), "护理", "1:2:3", 61_000L)

        assertNull(normal.sound)
        assertNull(normal.vibrate)
        assertEquals(normal.contentIntent, fallback.contentIntent)
        assertEquals(normal.actions.first().actionIntent, fallback.actions.first().actionIntent)
        val attributes = if (Build.VERSION.SDK_INT >= 26) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            assertEquals("countdown_test", normal.channelId)
            assertEquals("countdown_test_fallback", fallback.channelId)
            assertNull(notifications.getNotificationChannel(normal.channelId).sound)
            val channel = notifications.getNotificationChannel(fallback.channelId)
            assertEquals(Settings.System.DEFAULT_ALARM_ALERT_URI, channel.sound)
            assertFalse(channel.canBypassDnd())
            channel.audioAttributes
        } else {
            assertEquals(Settings.System.DEFAULT_ALARM_ALERT_URI, fallback.sound)
            fallback.audioAttributes
        }
        assertEquals(AudioAttributes.USAGE_ALARM, attributes.usage)
        assertEquals(0, attributes.flags and AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
    }

    @Test
    @Config(sdk = [28, 34])
    fun `existing fallback channel user silence is preserved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            "countdown_test_fallback", "muted by user", NotificationManager.IMPORTANCE_HIGH,
        ).apply { setSound(null, null) })
        val delegate = CountdownNotificationUiDelegate(context, "countdown_test", 3002, 3003)

        delegate.createNotificationChannel()
        delegate.buildCountdownFallbackNotification(OrderKey(123L, 4), "护理", "1:2:3", 61_000L)
        delegate.createNotificationChannel()

        assertNull(notifications.getNotificationChannel("countdown_test_fallback").sound)
    }

    @Test fun `notification preserves the same alarm identity for full screen display and tap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val delegate = CountdownNotificationUiDelegate(
            context = context,
            channelId = "countdown_test",
            dismissAlarmRequestCode = 3002,
            countdownAlarmActivityRequestCode = 3003,
        )
        val orderKey = OrderKey(123L, 4)
        val notification = delegate.buildCountdownCompletionNotification(
            orderKey, "护理", "1:2:3", 61_000L,
        )

        // 这里只验证请求交给系统，不用 JVM 测试声称系统一定弹页或显示 heads-up。
        assertNotNull(notification.fullScreenIntent)
        assertEquals(notification.fullScreenIntent, notification.contentIntent)
        val intent = shadowOf(notification.fullScreenIntent).savedIntent
        assertEquals(CountdownAlarmActivity::class.java.name, intent.component?.className)
        assertEquals(orderKey, CountdownNotificationManager.extractOrderKey(intent))
        assertEquals("1:2:3", intent.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER))
        assertEquals(61_000L, intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }
}
