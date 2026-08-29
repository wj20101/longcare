package com.ytone.longcare.features.countdown.manager

import android.content.Intent
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CountdownTaskCodecTest {
    private val codec = CountdownTaskCodec()
    private val orderKey = OrderKey(orderId = 88, planId = 9)

    @Test
    fun `same order across users has distinct alarm notification and dismiss identity`() {
        val taskA = codec.currentPayload(lease(1, 2, 3, 10, 1), orderKey, "A", 1_000)
        val taskB = codec.currentPayload(lease(1, 2, 4, 20, 2), orderKey, "B", 1_000)

        assertNotEquals(
            codec.dataUri(taskA.execution.taskIdentity, CountdownIntentPurpose.ALARM),
            codec.dataUri(taskB.execution.taskIdentity, CountdownIntentPurpose.ALARM),
        )
        assertNotEquals(
            codec.requestCode(taskA.execution.taskIdentity, CountdownIntentPurpose.ALARM),
            codec.requestCode(taskB.execution.taskIdentity, CountdownIntentPurpose.ALARM),
        )
        assertNotEquals(
            codec.completionNotificationId(taskA.execution.taskIdentity),
            codec.completionNotificationId(taskB.execution.taskIdentity),
        )
        assertNotEquals(
            codec.dataUri(taskA.execution.taskIdentity, CountdownIntentPurpose.DISMISS),
            codec.dataUri(taskA.execution.taskIdentity, CountdownIntentPurpose.ALARM),
        )
    }

    @Test
    fun `intent round trip validates purpose generation and business identity`() {
        val lease = lease(1, 2, 3, 10, 7)
        val payload = codec.currentPayload(lease, orderKey, "护理", 5_000)
        val intent = codec.writeToIntent(Intent(), payload, CountdownIntentPurpose.ALARM)

        assertEquals(payload, codec.fromIntent(intent, CountdownIntentPurpose.ALARM))
        assertNull(codec.fromIntent(intent, CountdownIntentPurpose.DISMISS))
        assertTrue(codec.matchesCurrent(payload, lease))
        assertFalse(codec.matchesCurrent(payload, lease.copy(generation = StorageGeneration(8))))
        assertFalse(intent.toUri(0).contains("token", ignoreCase = true))
    }

    private fun lease(
        companyId: Int,
        accountId: Int,
        userId: Int,
        epoch: Long,
        generation: Long,
    ) = UserStorageLease(
        scopeKey = UserScopeKey(companyId, accountId, userId),
        sessionEpoch = SessionEpoch(epoch),
        generation = StorageGeneration(generation),
    )
}
