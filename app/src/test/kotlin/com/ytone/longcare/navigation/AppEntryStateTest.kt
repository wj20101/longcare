package com.ytone.longcare.navigation

import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppEntryStateTest {
    @Test
    fun `privacy consent takes precedence over every session state`() {
        val loggedIn = SessionState.LoggedIn(currentUser(userId = 7))

        assertEquals(
            AppEntryState.ConsentRequired,
            resolveAppEntryState(isPrivacyConsented = false, sessionState = null),
        )
        assertEquals(
            AppEntryState.ConsentRequired,
            resolveAppEntryState(isPrivacyConsented = false, sessionState = SessionState.Unknown),
        )
        assertEquals(
            AppEntryState.ConsentRequired,
            resolveAppEntryState(isPrivacyConsented = false, sessionState = SessionState.LoggedOut),
        )
        assertEquals(
            AppEntryState.ConsentRequired,
            resolveAppEntryState(isPrivacyConsented = false, sessionState = loggedIn),
        )
    }

    @Test
    fun `consented entry maps unresolved logged out and logged in sessions`() {
        assertEquals(
            AppEntryState.ResolvingSession,
            resolveAppEntryState(isPrivacyConsented = true, sessionState = null),
        )
        assertEquals(
            AppEntryState.ResolvingSession,
            resolveAppEntryState(isPrivacyConsented = true, sessionState = SessionState.Unknown),
        )
        assertEquals(
            AppEntryState.LoggedOut,
            resolveAppEntryState(isPrivacyConsented = true, sessionState = SessionState.LoggedOut),
        )
        assertEquals(
            AppEntryState.LoggedIn,
            resolveAppEntryState(
                isPrivacyConsented = true,
                sessionState = SessionState.LoggedIn(currentUser(userId = 8)),
            ),
        )
    }

    @Test
    fun `startup roots only become ready for the resolved matching destination`() {
        val resolving = resolveStartupRootReadiness(
            entryState = AppEntryState.ResolvingSession,
            userIdentity = null,
        )
        val login = resolveStartupRootReadiness(
            entryState = AppEntryState.LoggedOut,
            userIdentity = null,
        )
        val care = resolveStartupRootReadiness(
            entryState = AppEntryState.LoggedIn,
            userIdentity = 1,
        )
        val sales = resolveStartupRootReadiness(
            entryState = AppEntryState.LoggedIn,
            userIdentity = 2,
        )

        assertEquals(false, resolving.isReady)
        assertEquals(true, isExpectedStartupRootReady(StartupRoot.Login, login))
        assertEquals(true, isExpectedStartupRootReady(StartupRoot.CareHome, care))
        assertEquals(true, isExpectedStartupRootReady(StartupRoot.SalesHome, sales))
        assertEquals(false, isExpectedStartupRootReady(StartupRoot.SalesHome, care))
        assertEquals(false, isExpectedStartupRootReady(StartupRoot.CareHome, sales))
    }

    @Test
    fun `privacy is interactive while logged in without a restored role remains unresolved`() {
        assertEquals(
            StartupRootReadiness(StartupRoot.Privacy, isReady = true),
            resolveStartupRootReadiness(AppEntryState.ConsentRequired, userIdentity = null),
        )
        assertEquals(
            StartupRootReadiness(StartupRoot.ResolvingSession, isReady = false),
            resolveStartupRootReadiness(AppEntryState.LoggedIn, userIdentity = null),
        )
    }

    private fun currentUser(userId: Int) = CurrentUser(
        scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = userId),
        userName = "User $userId",
        headUrl = "",
        userIdentity = 1,
        gender = 0,
    )
}
