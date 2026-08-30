package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.app.MainApplication
import com.ytone.longcare.common.utils.findBackStackEntryOrNull
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel

// ========== 主要Composable ==========

@Composable
fun MainApp(
    privacyConsentManager: PrivacyConsentManager? = null,
    viewModel: MainViewModel? = null,
) {
    val context = LocalContext.current
    val consentManager = privacyConsentManager
        ?: (context.applicationContext as MainApplication).privacyConsentManager
    var isConsented by rememberSaveable { mutableStateOf(consentManager.isPrivacyConsented) }

    // 首次启动时显示隐私政策同意弹窗
    if (!isConsented) {
        PrivacyConsentDialog(
            onAgree = {
                val application = context.applicationContext as? MainApplication
                if (application != null) {
                    application.onPrivacyConsentGranted()
                } else {
                    consentManager.markConsented()
                }
                isConsented = true
            },
            onDisagree = { /* Dialog 内部会 finish Activity */ }
        )
        return
    }

    val resolvedViewModel = viewModel ?: hiltViewModel<MainViewModel>()
    val sessionState by resolvedViewModel.sessionState.collectAsStateWithLifecycle()
    val appVersionModel by resolvedViewModel.appVersionModel.collectAsStateWithLifecycle()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val entryState = resolveAppEntryState(
        isPrivacyConsented = isConsented,
        sessionState = sessionState,
    )

    AppNavigation(entryState = entryState)

    appVersionModel?.let {
        AppUpdateDialog(
            appVersionModel = it,
            viewModel = updateViewModel,
            onDismiss = { resolvedViewModel.clearAppVersionModel() }
        )
    }
}

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    ReportStartupRootDrawn(
        expectedRoot = StartupRoot.ResolvingSession,
        actualReadiness = resolveStartupRootReadiness(
            entryState = AppEntryState.ResolvingSession,
            userIdentity = null,
        ),
    )
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun AppNavigation(entryState: AppEntryState) {
    val targetRoot = entryState.authenticationRootOrNull()
    var initialRootName by rememberSaveable { mutableStateOf(targetRoot?.name) }
    if (initialRootName == null && targetRoot != null) {
        initialRootName = targetRoot.name
    }
    val initialRoot = initialRootName?.let(AuthenticationRoot::valueOf)
    if (initialRoot == null) {
        SplashScreen()
        return
    }

    val navController = rememberNavController()
    Box(modifier = Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            startRoot = initialRoot,
            targetRoot = targetRoot,
        )
        if (targetRoot == null) {
            SplashScreen()
        }
    }
}

@Composable
internal fun AppNavHost(
    navController: NavHostController,
    startRoot: AuthenticationRoot,
    targetRoot: AuthenticationRoot?,
    entryDestinationRenderers: EntryDestinationRenderers = productionEntryDestinationRenderers,
) {
    val onLoginSuccess = remember(navController) {
        {
            reconcileAuthenticationRoot(navController, AuthenticationRoot.Home)
            Unit
        }
    }
    NavHost(
        navController = navController,
        startDestination = startRoot.route,
    ) {
        registerAppNavGraphs(
            navController = navController,
            onLoginSuccess = onLoginSuccess,
            entryDestinationRenderers = entryDestinationRenderers,
        )
    }

    LaunchedEffect(navController, targetRoot) {
        targetRoot?.let { reconcileAuthenticationRoot(navController, it) }
    }
}

internal fun reconcileAuthenticationRoot(
    navController: NavController,
    targetRoot: AuthenticationRoot,
): AuthenticationNavigationCommand {
    val backStackState = AuthenticationBackStackState(
        hasLoginRoot = navController.findBackStackEntryOrNull(LoginRoute) != null,
        hasHomeRoot = navController.findBackStackEntryOrNull(HomeGraphRoute) != null,
    )
    val command = resolveAuthenticationNavigationCommand(targetRoot, backStackState)
    when (command) {
        AuthenticationNavigationCommand.ShowHome -> navController.navigate(HomeGraphRoute) {
            if (backStackState.hasLoginRoot) {
                popUpTo(LoginRoute) { inclusive = true }
            }
            launchSingleTop = true
        }

        AuthenticationNavigationCommand.ShowLogin -> navController.navigate(LoginRoute) {
            if (backStackState.hasHomeRoot) {
                popUpTo(HomeGraphRoute) { inclusive = true }
            }
            launchSingleTop = true
        }

        AuthenticationNavigationCommand.NoOp -> Unit
    }
    return command
}

private val AuthenticationRoot.route: Any
    get() = when (this) {
        AuthenticationRoot.Login -> LoginRoute
        AuthenticationRoot.Home -> HomeGraphRoute
    }
