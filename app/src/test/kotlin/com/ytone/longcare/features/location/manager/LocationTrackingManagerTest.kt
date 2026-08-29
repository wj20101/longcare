package com.ytone.longcare.features.location.manager

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationTrackingManagerTest {
    private val reportingManager = mockk<LocationReportingManager>(relaxed = true)
    private val locationFacade = mockk<LocationFacade>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("com.ytone.longcare.common.utils.LogExtKt")
        every { any<Any>().logI(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.ytone.longcare.common.utils.LogExtKt")
    }

    @Test
    fun `tracking cannot start before a logged in session is known`() {
        val manager = createManager()

        manager.startTracking(OrderKey(100L))

        verify(exactly = 0) { reportingManager.startReporting(any()) }
    }

    @Test
    fun `permission grant cannot restart location without a logged in session`() {
        val manager = createManager()

        manager.startTrackingAfterPermissionGrant(OrderKey(100L))

        verify(exactly = 0) { locationFacade.notifyPermissionGranted() }
        verify(exactly = 0) { reportingManager.startReporting(any()) }
    }

    @Test
    fun `permission grant and start run together for a logged in session`() {
        val manager = createManager()
        val order = OrderKey(100L)
        manager.onSessionStateChanged(loggedIn(accountId = 2))

        manager.startTrackingAfterPermissionGrant(order)

        verify(exactly = 1) { locationFacade.notifyPermissionGranted() }
        verify(exactly = 1) { reportingManager.startReporting(order) }
    }

    @Test
    fun `logged in session allows start and logout stops then rejects restart`() {
        val manager = createManager()
        val order = OrderKey(100L)
        manager.onSessionStateChanged(loggedIn(accountId = 2))

        manager.startTracking(order)
        manager.onSessionStateChanged(SessionState.LoggedOut)
        manager.startTracking(OrderKey(200L))

        verify(exactly = 1) { reportingManager.startReporting(order) }
        verify(exactly = 1) { reportingManager.stopReporting() }
    }

    @Test
    fun `account switch stops the previous account reporting`() {
        val manager = createManager()
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.startTracking(OrderKey(100L))

        manager.onSessionStateChanged(loggedIn(accountId = 3))

        verify(exactly = 1) { reportingManager.stopReporting() }
    }

    @Test
    fun `runtime cleanup stops location and prevents an expired session restart`() = runTest {
        val manager = createManager()
        val order = OrderKey(100L)
        manager.onSessionStateChanged(loggedIn(accountId = 2))
        manager.startTracking(order)

        manager.cleanup(
            SessionRuntimeIdentity(
                scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = 2),
                sessionEpoch = SessionEpoch(10),
            )
        )
        manager.startTracking(OrderKey(200L))

        verify(exactly = 1) { reportingManager.startReporting(order) }
        verify(exactly = 1) { reportingManager.stopReporting() }
    }

    @Test
    fun `logout is serialized after an in progress start and wins final state`() {
        val startEntered = CountDownLatch(1)
        val allowStartToFinish = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        every { reportingManager.startReporting(any()) } answers {
            events += "start-begin"
            startEntered.countDown()
            check(allowStartToFinish.await(2, TimeUnit.SECONDS))
            events += "start-end"
        }
        every { reportingManager.stopReporting() } answers { events += "stop" }
        val manager = createManager()
        manager.onSessionStateChanged(loggedIn(accountId = 2))

        val startThread = Thread { manager.startTracking(OrderKey(100L)) }
        startThread.start()
        assertTrue(startEntered.await(2, TimeUnit.SECONDS))
        val logoutThread = Thread { manager.onSessionStateChanged(SessionState.LoggedOut) }
        logoutThread.start()
        allowStartToFinish.countDown()
        startThread.join(2_000)
        logoutThread.join(2_000)
        manager.startTracking(OrderKey(200L))

        assertEquals(listOf("start-begin", "start-end", "stop"), events)
        verify(exactly = 1) { reportingManager.startReporting(any()) }
    }

    private fun createManager() = LocationTrackingManager(
        locationFacade = locationFacade,
        locationReportingManager = reportingManager,
    )

    private fun loggedIn(accountId: Int) = SessionState.LoggedIn(
        CurrentUser(
            scopeKey = UserScopeKey(companyId = 1, accountId = accountId, userId = accountId),
            userName = "user-$accountId",
            headUrl = "",
            userIdentity = 1,
            gender = 0,
        ),
    )
}
