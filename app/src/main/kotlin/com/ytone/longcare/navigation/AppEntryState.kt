package com.ytone.longcare.navigation

import com.ytone.longcare.domain.repository.SessionState

internal sealed interface AppEntryState {
    data object ConsentRequired : AppEntryState

    data object ResolvingSession : AppEntryState

    data object LoggedOut : AppEntryState

    data object LoggedIn : AppEntryState
}

internal enum class StartupRoot {
    Privacy,
    ResolvingSession,
    Login,
    CareHome,
    SalesHome,
}

internal data class StartupRootReadiness(
    val root: StartupRoot,
    val isReady: Boolean,
)

internal fun resolveStartupRootReadiness(
    entryState: AppEntryState,
    userIdentity: Int?,
): StartupRootReadiness = when (entryState) {
    AppEntryState.ConsentRequired -> StartupRootReadiness(StartupRoot.Privacy, isReady = true)
    AppEntryState.ResolvingSession ->
        StartupRootReadiness(StartupRoot.ResolvingSession, isReady = false)
    AppEntryState.LoggedOut -> StartupRootReadiness(StartupRoot.Login, isReady = true)
    AppEntryState.LoggedIn -> when (userIdentity) {
        null -> StartupRootReadiness(StartupRoot.ResolvingSession, isReady = false)
        2 -> StartupRootReadiness(StartupRoot.SalesHome, isReady = true)
        else -> StartupRootReadiness(StartupRoot.CareHome, isReady = true)
    }
}

internal fun isExpectedStartupRootReady(
    expected: StartupRoot,
    actual: StartupRootReadiness,
): Boolean = actual.isReady && actual.root == expected

internal fun resolveAppEntryState(
    isPrivacyConsented: Boolean,
    sessionState: SessionState?,
): AppEntryState {
    if (!isPrivacyConsented) return AppEntryState.ConsentRequired

    return when (sessionState) {
        null,
        SessionState.Unknown,
        -> AppEntryState.ResolvingSession

        SessionState.LoggedOut -> AppEntryState.LoggedOut
        is SessionState.LoggedIn -> AppEntryState.LoggedIn
    }
}
