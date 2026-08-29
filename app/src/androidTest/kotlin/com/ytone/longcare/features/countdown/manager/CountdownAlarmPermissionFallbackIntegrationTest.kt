package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CountdownAlarmPermissionFallbackIntegrationTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private val codec = CountdownTaskCodec()
    private val payload by lazy {
        codec.currentPayload(
            UserStorageLease(
                UserScopeKey(901, 902, 903),
                SessionEpoch(904),
                StorageGeneration(905),
            ),
            OrderKey(orderId = ORDER_ID, planId = 1),
            "精确闹钟权限降级测试",
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10),
        )
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @After
    fun tearDown() {
        cancelCountdownAlarmForIdentity(
            context = context,
            alarmManager = alarmManager,
            codec = codec,
            identity = payload.execution.taskIdentity,
        )
    }

    @Test
    fun noExactAlarmPermission_fallsBackWithoutThrowing() {
        val metadata =
            scheduleCountdownAlarmInSystem(
                context = context,
                alarmManager = alarmManager,
                codec = codec,
                payload = payload,
                canUseExactAlarm = false,
            )

        assertNotNull(metadata)
        assertFalse(metadata!!.useAlarmClock)
    }

    private companion object {
        const val ORDER_ID = 987_654_321L
    }
}
