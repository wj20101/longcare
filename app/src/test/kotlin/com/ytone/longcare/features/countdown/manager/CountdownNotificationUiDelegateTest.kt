package com.ytone.longcare.features.countdown.manager

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownNotificationUiDelegateTest {
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
