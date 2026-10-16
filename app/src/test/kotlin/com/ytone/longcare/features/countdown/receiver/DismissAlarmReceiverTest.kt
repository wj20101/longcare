package com.ytone.longcare.features.countdown.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DismissAlarmReceiverTest {
    @Test fun `dismiss passes immutable notification identity to manager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = mockk<CountdownNotificationManager>(relaxed = true)
        val orderKey = OrderKey(54321L, 21)
        val intent = Intent().apply {
            putExtra("extra_request", orderKey)
            putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, "1:2:3")
            putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 1234L)
        }
        DismissAlarmReceiverDelegate.handle(context, intent, manager)
        verify(exactly = 1) { manager.dismissAlarm(orderKey, "1:2:3", 1234L) }
    }
}
