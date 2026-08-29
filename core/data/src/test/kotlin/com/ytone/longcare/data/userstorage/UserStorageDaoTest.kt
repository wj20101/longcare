package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.PendingServiceReminderEntityDb
import com.ytone.longcare.data.database.entity.UserNamespaceMetadataEntityDb
import com.ytone.longcare.domain.userstorage.COUNTDOWN_TASK_TYPE
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserStorageDaoTest {
    private lateinit var database: LongCareDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LongCareDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `namespace metadata round trips through its single row`() = runTest {
        val expected = UserNamespaceMetadataEntityDb(
            formatVersion = 1,
            namespaceId = "v1_${"a".repeat(64)}",
            companyId = 10,
            accountId = 20,
            userId = 30,
        )

        assertNull(database.userNamespaceMetadataDao().get())
        database.userNamespaceMetadataDao().insert(expected)

        assertEquals(expected, database.userNamespaceMetadataDao().get())
    }

    @Test
    fun `pending reminders upsert observe delete and clear independently by order`() = runTest {
        val dao = database.pendingServiceReminderDao()
        val first = reminder(orderId = 11, triggerAt = 200)
        val second = reminder(orderId = 22, triggerAt = 100)

        dao.upsert(first)
        dao.upsert(second)
        assertEquals(
            listOf(second, first),
            dao.observeAll(sessionEpoch = 7, taskType = SERVICE_TIME_END_TASK_TYPE).first(),
        )

        val updated = first.copy(serviceName = "updated", triggerAtMillis = 50)
        dao.upsert(updated)
        assertEquals(
            listOf(updated, second),
            dao.getAll(sessionEpoch = 7, taskType = SERVICE_TIME_END_TASK_TYPE),
        )

        dao.delete(second.taskIdentity)
        assertEquals(
            listOf(updated),
            dao.getAll(sessionEpoch = 7, taskType = SERVICE_TIME_END_TASK_TYPE),
        )
        dao.upsert(
            reminder(orderId = 33, triggerAt = 300).copy(
                taskIdentity = "countdown-33",
                taskType = COUNTDOWN_TASK_TYPE,
                businessId = "33:3",
            ),
        )
        dao.deleteAll(sessionEpoch = 7, taskType = SERVICE_TIME_END_TASK_TYPE)
        assertEquals(
            emptyList<PendingServiceReminderEntityDb>(),
            dao.getAll(sessionEpoch = 7, taskType = SERVICE_TIME_END_TASK_TYPE),
        )
        assertEquals(
            1,
            dao.getAll(sessionEpoch = 7, taskType = COUNTDOWN_TASK_TYPE).size,
        )
    }

    private fun reminder(orderId: Long, triggerAt: Long) = PendingServiceReminderEntityDb(
        taskIdentity = "task-$orderId",
        orderId = orderId,
        serviceName = "service-$orderId",
        triggerAtMillis = triggerAt,
        sessionEpoch = 7,
        storageGeneration = 1,
        taskType = SERVICE_TIME_END_TASK_TYPE,
        businessId = orderId.toString(),
        createdAtMillis = 8,
    )
}
