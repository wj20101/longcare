package com.ytone.longcare.features.service

import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.UserScopeKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTimeEndWorkerTaskGateTest {
    @Test
    fun `worker dispatch silently terminates invalid and expired task before manager side effects`() = runTest {
        val gate = mockk<ServiceTimeTaskExecutionGate>()
        val manager = mockk<ServiceTimeNotificationManager>()
        val payload = payload()
        every { gate.isCurrent(payload) } returns false

        assertFalse(dispatchServiceTimeWorkerPayload(null, gate, manager))
        assertFalse(dispatchServiceTimeWorkerPayload(payload, gate, manager))

        coVerify(exactly = 0) { manager.handleTriggered(any()) }
    }

    @Test
    fun `worker dispatch delegates only an exact current task`() = runTest {
        val gate = mockk<ServiceTimeTaskExecutionGate>()
        val manager = mockk<ServiceTimeNotificationManager>()
        val payload = payload()
        every { gate.isCurrent(payload) } returns true
        coEvery { manager.handleTriggered(payload) } returns true

        assertTrue(dispatchServiceTimeWorkerPayload(payload, gate, manager))

        coVerify(exactly = 1) { manager.handleTriggered(payload) }
    }

    private fun payload(): ServiceTimeTaskPayload {
        val lease = UserStorageLease(
            UserScopeKey(1, 2, 3),
            SessionEpoch(4),
            StorageGeneration(5),
        )
        return ServiceTimeTaskPayload(
            execution = ServiceTimeTaskCodec().currentExecution(lease, 6),
            serviceName = "service",
            triggerAtMillis = 7,
        )
    }
}
