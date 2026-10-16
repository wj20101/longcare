package com.ytone.longcare.presentation.countdown

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.internal.managers.ActivityComponentManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownAlarmActivityTest {
    private lateinit var context: Context
    private lateinit var manager: CountdownNotificationManager
    private var controller: ActivityController<CountdownAlarmActivity>? = null

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = mockk(relaxed = true)
        coEvery { manager.awaitResolvedSession() } returns true
        every { manager.ownsAlarm(any<Intent>()) } returns true
        every { manager.ownsAlarm(any<OrderKey>(), any(), any()) } returns true
    }

    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        unmockkAll()
    }

    @Test fun `single top new intent replaces displayed order and stale intent cannot close it`() {
        val first = alarmIntent(123L, "第一笔")
        val activity = createActivity(first)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(displayedContent(activity).contains("第一笔"))
        val next = alarmIntent(456L, "第二笔")
        controller?.newIntent(next)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(displayedContent(activity).contains("第二笔"))
        assertFalse(displayedContent(activity).contains("第一笔"))
        every { manager.ownsAlarm(first) } returns false
        controller?.newIntent(first)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
        assertTrue(displayedContent(activity).contains("第二笔"))
        assertEquals(next, activity.intent)
        verify(exactly = 0) { manager.dismissAlarm(any(), any(), any()) }
    }

    @Test fun `temporary obscuring does not dismiss the reminder`() {
        createActivity(alarmIntent(123L, "护理"))
        shadowOf(Looper.getMainLooper()).idle()
        controller?.pause()?.stop()
        verify(exactly = 0) { manager.dismissAlarm(any(), any(), any()) }
        controller?.start()?.resume()
    }

    private fun createActivity(intent: Intent): CountdownAlarmActivity {
        val created = Robolectric.buildActivity(CountdownAlarmActivity::class.java, intent)
        val activity = created.get()
        // Hilt 2.60 还会初始化 saved-state holder；仅替换 DI 组件脚手架，业务生命周期保持真实。
        ReflectionHelpers.setField(activity, "injected", true)
        ReflectionHelpers.setField(activity, "componentManager", mockk<ActivityComponentManager>(relaxed = true))
        activity.countdownNotificationManager = manager
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        created.create().start().resume()
        controller = created
        return activity
    }

    private fun displayedContent(activity: CountdownAlarmActivity): String {
        val getter = CountdownAlarmActivity::class.java.getDeclaredMethod("getContent")
        getter.isAccessible = true
        return requireNotNull(getter.invoke(activity)) { "Expected an owned alarm to be displayed" }.toString()
    }

    private fun alarmIntent(id: Long, name: String) = CountdownAlarmActivity.createIntent(
        context, OrderKey(id, 1), name, "1:2:3", 1234L)
}
