package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProfileRepositoryImplTest {

    @Test
    fun `remote success always performs face and session cleanup`() = runTest {
        val fixture = fixture(ApiResult.Success(Unit))

        val result = fixture.repository.logout()

        assertEquals(ApiResult.Success(Unit), result)
        fixture.verifyLocalCleanup()
    }

    @Test
    fun `remote failure result always performs face and session cleanup`() = runTest {
        val failure = ApiResult.Failure(500, "remote failure")
        val fixture = fixture(failure)

        val result = fixture.repository.logout()

        assertEquals(failure, result)
        fixture.verifyLocalCleanup()
    }

    @Test
    fun `remote exception always performs local cleanup before propagating`() = runTest {
        val failure = IllegalStateException("network exploded")
        val fixture = fixture(remoteThrowable = failure)

        try {
            fixture.repository.logout()
            fail("Expected remote exception")
        } catch (actual: IllegalStateException) {
            assertEquals(failure, actual)
        }

        fixture.verifyLocalCleanup()
    }

    @Test
    fun `remote cancellation always performs local cleanup and remains cancellation`() = runTest {
        val fixture = fixture(remoteThrowable = CancellationException("cancelled"))

        try {
            fixture.repository.logout()
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertEquals("cancelled", actual.message)
        }

        fixture.verifyLocalCleanup()
    }

    @Test
    fun `local cleanup cancellation is rethrown after session logout completes`() = runTest {
        val fixture = fixture(
            remoteResult = ApiResult.Success(Unit),
            cleanerThrowable = CancellationException("cleanup cancelled"),
        )

        try {
            fixture.repository.logout()
            fail("Expected cleanup cancellation")
        } catch (actual: CancellationException) {
            assertEquals("cleanup cancelled", actual.message)
        }

        fixture.verifyLocalCleanup()
    }

    private fun fixture(
        remoteResult: ApiResult<Unit>? = null,
        remoteThrowable: Throwable? = null,
        cleanerThrowable: Throwable? = null,
    ): Fixture {
        val api = mockk<LongCareApiService>()
        if (remoteThrowable != null) {
            coEvery { api.logout() } throws remoteThrowable
        } else {
            coEvery { api.logout() } returns checkNotNull(remoteResult)
        }
        val session = FakeSessionRepository()
        val cleaner = mockk<FaceCacheCleaner>()
        if (cleanerThrowable != null) {
            coEvery { cleaner.clearUserFaceArtifacts(any()) } throws cleanerThrowable
        } else {
            coEvery { cleaner.clearUserFaceArtifacts(any()) } returns Unit
        }
        return Fixture(
            repository = ProfileRepositoryImpl(api, session, cleaner),
            session = session,
            cleaner = cleaner,
        )
    }

    private data class Fixture(
        val repository: ProfileRepositoryImpl,
        val session: FakeSessionRepository,
        val cleaner: FaceCacheCleaner,
    ) {
        fun verifyLocalCleanup() {
            coVerify(exactly = 1) { cleaner.clearUserFaceArtifacts(7) }
            assertEquals(1, session.logoutCalls)
        }
    }

    private class FakeSessionRepository : UserSessionRepository {
        private val mutableState = MutableStateFlow<SessionState>(
            SessionState.LoggedIn(
                CurrentUser(
                    scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = 7),
                    userName = "current",
                    headUrl = "",
                    userIdentity = 1,
                    gender = 0,
                )
            )
        )
        override val sessionState: StateFlow<SessionState> = mutableState
        var logoutCalls: Int = 0
            private set

        override suspend fun login(payload: SessionLoginPayload) = Unit

        override suspend fun logout() {
            logoutCalls += 1
            mutableState.value = SessionState.LoggedOut
        }
    }
}
