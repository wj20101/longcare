package com.ytone.longcare.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticationNavigationCoordinatorTest {
    @Test
    fun unresolvedEntryStatesDoNotSelectAnAuthenticationRoot() {
        assertNull(AppEntryState.ConsentRequired.authenticationRootOrNull())
        assertNull(AppEntryState.ResolvingSession.authenticationRootOrNull())
    }

    @Test
    fun resolvedEntryStatesSelectTheirAuthenticationRoot() {
        assertEquals(AuthenticationRoot.Login, AppEntryState.LoggedOut.authenticationRootOrNull())
        assertEquals(AuthenticationRoot.Home, AppEntryState.LoggedIn.authenticationRootOrNull())
    }

    @Test
    fun loginTargetReplacesHomeRoot() {
        assertEquals(
            AuthenticationNavigationCommand.ShowLogin,
            resolveAuthenticationNavigationCommand(
                targetRoot = AuthenticationRoot.Login,
                backStackState = AuthenticationBackStackState(
                    hasLoginRoot = false,
                    hasHomeRoot = true,
                ),
            ),
        )
    }

    @Test
    fun homeTargetReplacesLoginRoot() {
        assertEquals(
            AuthenticationNavigationCommand.ShowHome,
            resolveAuthenticationNavigationCommand(
                targetRoot = AuthenticationRoot.Home,
                backStackState = AuthenticationBackStackState(
                    hasLoginRoot = true,
                    hasHomeRoot = false,
                ),
            ),
        )
    }

    @Test
    fun repeatedLoginSuccessAndSameStateEmissionsAreNoOp() {
        assertEquals(
            AuthenticationNavigationCommand.NoOp,
            resolveAuthenticationNavigationCommand(
                targetRoot = AuthenticationRoot.Home,
                backStackState = AuthenticationBackStackState(
                    hasLoginRoot = false,
                    hasHomeRoot = true,
                ),
            ),
        )
        assertEquals(
            AuthenticationNavigationCommand.NoOp,
            resolveAuthenticationNavigationCommand(
                targetRoot = AuthenticationRoot.Login,
                backStackState = AuthenticationBackStackState(
                    hasLoginRoot = true,
                    hasHomeRoot = false,
                ),
            ),
        )
    }

    @Test
    fun missingOrConflictingRootsAreReconciledToTheTarget() {
        val missingRoots = AuthenticationBackStackState(
            hasLoginRoot = false,
            hasHomeRoot = false,
        )
        val conflictingRoots = AuthenticationBackStackState(
            hasLoginRoot = true,
            hasHomeRoot = true,
        )

        assertEquals(
            AuthenticationNavigationCommand.ShowHome,
            resolveAuthenticationNavigationCommand(AuthenticationRoot.Home, missingRoots),
        )
        assertEquals(
            AuthenticationNavigationCommand.ShowLogin,
            resolveAuthenticationNavigationCommand(AuthenticationRoot.Login, conflictingRoots),
        )
    }
}
