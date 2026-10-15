package com.ytone.longcare.features.location.core

import com.ytone.longcare.domain.location.*
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.repository.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.model.LocationResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultLocationFacadeCancellationTest {
    private val manager = mockk<ContinuousAmapLocationManager>()
    private val readiness = mockk<LocationRuntimeReadiness> {
        every { hasLocationPermission() } returns true
        every { isLocationServiceEnabled() } returns true
    }
    private val session = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    private val users = mockk<UserSessionRepository> { every { sessionState } returns session }
    private val facade = DefaultLocationFacade(manager, readiness, users)

    @Test fun `each action requests new location and failures never return old success`() = runTest {
        val success = LocationAcquisition.Success(LocationResult(30.0, 120.0, "test"))
        val failure = LocationAcquisition.Failure(LocationFailure.TIMEOUT)
        coEvery { manager.acquireCurrentLocation() } returnsMany listOf(success, failure)
        assertEquals(success, facade.acquireCurrentLocation())
        assertEquals(failure, facade.acquireCurrentLocation())
        coVerify(exactly = 2) { manager.acquireCurrentLocation() }
    }

    @Test fun `denied permission and disabled location never start SDK`() = runTest {
        every { readiness.hasLocationPermission() } returns false
        assertEquals(LocationAcquisition.Failure(LocationFailure.PERMISSION), facade.acquireCurrentLocation())
        every { readiness.hasLocationPermission() } returns true
        every { readiness.isLocationServiceEnabled() } returns false
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), facade.acquireCurrentLocation())
        coVerify(exactly = 0) { manager.acquireCurrentLocation() }
    }

    @Test fun `permission revoked during request discards result`() = runTest {
        coEvery { manager.acquireCurrentLocation() } coAnswers {
            every { readiness.hasLocationPermission() } returns false
            LocationAcquisition.Success(LocationResult(30.0, 120.0, "test"))
        }
        assertEquals(LocationAcquisition.Failure(LocationFailure.PERMISSION), facade.acquireCurrentLocation())
    }

    @Test fun `cancellation propagates to the caller`() = runTest {
        coEvery { manager.acquireCurrentLocation() } throws CancellationException("SDK request cancelled")
        try {
            facade.acquireCurrentLocation()
            fail("Cancellation expected")
        } catch (cancelled: CancellationException) {
            assertEquals("SDK request cancelled", cancelled.message)
        }
    }
    @Test fun `account change cancels in flight SDK work`() = runTest {
        var released = false
        coEvery { manager.acquireCurrentLocation() } coAnswers {
            try { kotlinx.coroutines.awaitCancellation() } finally { released = true }
        }
        val request = async { facade.acquireCurrentLocation() }
        runCurrent()
        session.value = SessionState.LoggedIn(com.ytone.longcare.model.User(userId = 1))
        runCurrent()
        assertTrue(request.isCancelled)
        assertTrue(released)
    }

    @Test fun `service order request checks permission before SDK and after result`() = runTest {
        every { readiness.hasLocationPermission() } returns false
        assertEquals(LocationAcquisition.Failure(LocationFailure.PERMISSION), facade.acquireServiceOrderLocation())
        coVerify(exactly = 0) { manager.acquireCurrentLocation(true) }
        every { readiness.hasLocationPermission() } returns true
        coEvery { manager.acquireCurrentLocation(true) } coAnswers {
            every { readiness.hasLocationPermission() } returns false
            LocationAcquisition.Success(LocationResult(30.0, 120.0, "test"))
        }
        assertEquals(LocationAcquisition.Failure(LocationFailure.PERMISSION), facade.acquireServiceOrderLocation())
        coVerify(exactly = 1) { manager.acquireCurrentLocation(true) }
    }

    @Test fun `account change cancels service order request without touching continuous client`() = runTest {
        var cleaned = false
        coEvery { manager.acquireCurrentLocation(true) } coAnswers {
            try { kotlinx.coroutines.awaitCancellation() } finally { cleaned = true }
        }
        val request = async { facade.acquireServiceOrderLocation() }
        runCurrent()
        session.value = SessionState.LoggedIn(com.ytone.longcare.model.User(userId = 2))
        runCurrent()
        assertTrue(request.isCancelled)
        assertTrue(cleaned)
    }

}
