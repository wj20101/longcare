package com.ytone.longcare.navigation

internal enum class AuthenticationRoot {
    Login,
    Home,
}

internal data class AuthenticationBackStackState(
    val hasLoginRoot: Boolean,
    val hasHomeRoot: Boolean,
)

internal sealed interface AuthenticationNavigationCommand {
    data object ShowLogin : AuthenticationNavigationCommand

    data object ShowHome : AuthenticationNavigationCommand

    data object NoOp : AuthenticationNavigationCommand
}

internal fun AppEntryState.authenticationRootOrNull(): AuthenticationRoot? = when (this) {
    AppEntryState.LoggedOut -> AuthenticationRoot.Login
    AppEntryState.LoggedIn -> AuthenticationRoot.Home
    AppEntryState.ConsentRequired,
    AppEntryState.ResolvingSession,
    -> null
}

internal fun resolveAuthenticationNavigationCommand(
    targetRoot: AuthenticationRoot,
    backStackState: AuthenticationBackStackState,
): AuthenticationNavigationCommand = when (targetRoot) {
    AuthenticationRoot.Login -> {
        if (backStackState.hasLoginRoot && !backStackState.hasHomeRoot) {
            AuthenticationNavigationCommand.NoOp
        } else {
            AuthenticationNavigationCommand.ShowLogin
        }
    }

    AuthenticationRoot.Home -> {
        if (backStackState.hasHomeRoot && !backStackState.hasLoginRoot) {
            AuthenticationNavigationCommand.NoOp
        } else {
            AuthenticationNavigationCommand.ShowHome
        }
    }
}
