package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.data.repository.ProfileRepositoryImpl
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.features.location.session.LocationSessionLifecycleObserver
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationLogoutIntegrationRegressionTest {
    @Test
    fun `restored login state is delegated but never resumes a location session`() = runTest {
        val restored = SessionState.LoggedIn(User(companyId = 1, accountId = 2, userId = 7))
        val sessionFlow = MutableStateFlow<SessionState>(restored)
        val sessionRepository = mockk<UserSessionRepository>()
        every { sessionRepository.sessionState } returns sessionFlow
        val locationManager = mockk<LocationTrackingManager>(relaxed = true)

        LocationSessionLifecycleObserver(
            applicationScope = backgroundScope,
            userSessionRepository = sessionRepository,
            locationTrackingManager = locationManager,
        ).start()
        runCurrent()

        verify(exactly = 1) { locationManager.onSessionStateChanged(restored) }
        verify(exactly = 0) { locationManager.startTracking(any()) }
    }

    @Test
    fun `switching account stops active process location`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(
            SessionState.LoggedIn(User(companyId = 1, accountId = 2, userId = 7)),
        )
        val sessionRepository = mockk<UserSessionRepository>()
        every { sessionRepository.sessionState } returns sessionFlow
        val locationManager = mockk<LocationTrackingManager>(relaxed = true)
        LocationSessionLifecycleObserver(
            applicationScope = backgroundScope,
            userSessionRepository = sessionRepository,
            locationTrackingManager = locationManager,
        ).start()
        runCurrent()

        val switched = SessionState.LoggedIn(
            User(companyId = 1, accountId = 3, userId = 8),
        )
        sessionFlow.value = switched
        runCurrent()

        verify(exactly = 1) { locationManager.onSessionStateChanged(switched) }
        verify(exactly = 0) { locationManager.startTracking(any()) }
    }

    @Test
    fun `manual logout stops process location without scheduling recovery work`() = runTest {
        val user = User(companyId = 1, accountId = 2, userId = 7, token = "token")
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.LoggedIn(user))
        val sessionRepository = mockk<UserSessionRepository>()
        every { sessionRepository.sessionState } returns sessionFlow
        coEvery { sessionRepository.logout() } coAnswers { sessionFlow.value = SessionState.LoggedOut }

        val locationManager = mockk<LocationTrackingManager>(relaxed = true)
        val observer = LocationSessionLifecycleObserver(
            applicationScope = backgroundScope,
            userSessionRepository = sessionRepository,
            locationTrackingManager = locationManager,
        )
        observer.start()
        runCurrent()

        val api = mockk<LongCareApiService>()
        coEvery { api.logout() } returns ApiResult.Success(Unit)
        val cleaner = mockk<FaceCacheCleaner>()
        coEvery { cleaner.clearUserFaceBase64(any()) } just runs
        ProfileRepositoryImpl(api, sessionRepository, cleaner).logout()
        runCurrent()

        verify(exactly = 1) { locationManager.onSessionStateChanged(SessionState.LoggedOut) }
        coVerify(exactly = 1) { sessionRepository.logout() }
    }
}
