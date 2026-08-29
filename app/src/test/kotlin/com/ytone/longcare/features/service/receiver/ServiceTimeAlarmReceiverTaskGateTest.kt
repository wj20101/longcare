package com.ytone.longcare.features.service.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.ServiceTimeTaskCodec
import com.ytone.longcare.features.service.ServiceTimeTaskExecutionGate
import com.ytone.longcare.features.service.ServiceTimeTaskPayload
import com.ytone.longcare.model.UserScopeKey
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceTimeAlarmReceiverTaskGateTest {
    @Test
    fun `expired alarm returns before wake lock or notification manager work`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val codec = ServiceTimeTaskCodec()
        val gate = mockk<ServiceTimeTaskExecutionGate>()
        val manager = mockk<ServiceTimeNotificationManager>(relaxed = true)
        val payload = payload(codec)
        every { gate.isCurrent(payload) } returns false
        val receiver = ServiceTimeAlarmReceiver().apply {
            taskCodec = codec
            executionGate = gate
            serviceTimeNotificationManager = manager
            applicationScope = backgroundScope
        }
        val intent = codec.writeToAlarmIntent(
            Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
            },
            payload,
        )

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { manager.handleTriggered(any()) }
    }

    private fun payload(codec: ServiceTimeTaskCodec): ServiceTimeTaskPayload {
        val lease = UserStorageLease(
            UserScopeKey(1, 2, 3),
            SessionEpoch(4),
            StorageGeneration(5),
        )
        return ServiceTimeTaskPayload(
            execution = codec.currentExecution(lease, 6),
            serviceName = "service",
            triggerAtMillis = 7,
        )
    }
}
