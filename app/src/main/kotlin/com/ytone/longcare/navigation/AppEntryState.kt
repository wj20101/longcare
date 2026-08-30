package com.ytone.longcare.navigation

import com.ytone.longcare.domain.repository.SessionState

internal sealed interface AppEntryState {
    data object ConsentRequired : AppEntryState

    data object ResolvingSession : AppEntryState

    data object LoggedOut : AppEntryState

    data object LoggedIn : AppEntryState
}

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
