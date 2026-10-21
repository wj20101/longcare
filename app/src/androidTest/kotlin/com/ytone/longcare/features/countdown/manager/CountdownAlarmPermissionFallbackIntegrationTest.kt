package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CountdownAlarmPermissionFallbackIntegrationTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @After
    fun tearDown() {
        cancelCountdownAlarmForOrderId(
            context = context,
            alarmManager = alarmManager,
            countdownAlarmRequestCode = ALARM_REQUEST_CODE,
            actionCountdownAlarmPrefix = ACTION_PREFIX,
            orderId = ORDER_ID,
        )
    }

    @Test
    fun noExactAlarmPermission_fallsBackWithoutThrowing() {
        val metadata =
            scheduleCountdownAlarmInSystem(
                context = context,
                alarmManager = alarmManager,
                countdownAlarmRequestCode = ALARM_REQUEST_CODE,
                actionCountdownAlarmPrefix = ACTION_PREFIX,
                orderKey = OrderKey(orderId = ORDER_ID),
                serviceName = "精确闹钟权限降级测试",
                triggerTimeMillis =
                    System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10),
                canUseExactAlarm = false,
            )

        assertNotNull(metadata)
        val fields = CountdownAlarmDiagnostics.fields(metadata!!.diagnostics, 0L, "scheduled")
        assertEquals(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "inexact_allow_idle" else "exact_allow_idle",
            fields["scheduleMode"],
        )
    }

    private companion object {
        const val ORDER_ID = 987_654_321L
        const val ALARM_REQUEST_CODE = 31_001
        const val ACTION_PREFIX = "com.ytone.longcare.test.COUNTDOWN_ALARM_"
    }
}
