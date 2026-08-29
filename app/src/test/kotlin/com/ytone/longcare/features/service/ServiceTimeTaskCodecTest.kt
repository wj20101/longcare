package com.ytone.longcare.features.service

import android.content.Intent
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
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
class ServiceTimeTaskCodecTest {
    private val codec = ServiceTimeTaskCodec()

    @Test
    fun `all platform identities include namespace epoch and business ID`() {
        val a = payload(lease(UserScopeKey(1, 2, 3), epoch = 10, generation = 20), orderId = 99)
        val b = payload(lease(UserScopeKey(1, 2, 4), epoch = 10, generation = 21), orderId = 99)

        assertNotEquals(codec.workUniqueName(a.execution.taskIdentity), codec.workUniqueName(b.execution.taskIdentity))
        assertNotEquals(codec.workTag(a.execution.taskIdentity), codec.workTag(b.execution.taskIdentity))
        assertNotEquals(codec.alarmDataUri(a.execution.taskIdentity), codec.alarmDataUri(b.execution.taskIdentity))
        assertNotEquals(codec.alarmRequestCode(a.execution.taskIdentity), codec.alarmRequestCode(b.execution.taskIdentity))
        assertNotEquals(codec.notificationId(a.execution.taskIdentity), codec.notificationId(b.execution.taskIdentity))
        assertNotEquals(codec.deduplicationKey(a.execution.taskIdentity), codec.deduplicationKey(b.execution.taskIdentity))

        val workRoundTrip = codec.fromWorkData(codec.toWorkData(a))
        val alarmIntent = codec.writeToAlarmIntent(Intent("alarm"), a)
        assertEquals(a, workRoundTrip)
        assertEquals(a, codec.fromAlarmIntent(alarmIntent))
        assertTrue(codec.matchesCurrent(a.execution, lease(UserScopeKey(1, 2, 3), 10, 20)))
    }

    @Test
    fun `generation is validated but does not change persistent task identity`() {
        val firstLease = lease(UserScopeKey(1, 2, 3), epoch = 10, generation = 20)
        val nextGeneration = lease(UserScopeKey(1, 2, 3), epoch = 10, generation = 21)
        val first = payload(firstLease, orderId = 99)
        val next = payload(nextGeneration, orderId = 99)

        assertEquals(codec.workUniqueName(first.execution.taskIdentity), codec.workUniqueName(next.execution.taskIdentity))
        assertEquals(codec.alarmDataUri(first.execution.taskIdentity), codec.alarmDataUri(next.execution.taskIdentity))
        assertFalse(codec.matchesCurrent(first.execution, nextGeneration))
    }

    @Test
    fun `tampered alarm and secret-free persisted input fail closed`() {
        val payload = payload(lease(UserScopeKey(1, 2, 3), 10, 20), orderId = 99)
        val intent = codec.writeToAlarmIntent(Intent("alarm"), payload).apply {
            data = requireNotNull(data).buildUpon().appendPath("tampered").build()
        }

        assertNull(codec.fromAlarmIntent(intent))
        assertFalse(codec.toWorkData(payload).keyValueMap.keys.any { key ->
            key.contains("token", ignoreCase = true) || key.contains("identityCard", ignoreCase = true)
        })
        assertFalse(payload.execution.taskIdentity.encode().contains("secret-token"))
    }

    private fun payload(lease: UserStorageLease, orderId: Long) = ServiceTimeTaskPayload(
        execution = codec.currentExecution(lease, orderId),
        serviceName = "service",
        triggerAtMillis = 1_000,
    )

    private fun lease(
        scope: UserScopeKey,
        epoch: Long,
        generation: Long,
    ) = UserStorageLease(scope, SessionEpoch(epoch), StorageGeneration(generation))
}
