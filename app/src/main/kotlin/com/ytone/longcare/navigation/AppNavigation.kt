package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.app.MainApplication
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel

private fun resolveStartDestination(sessionState: SessionState): AppRoute? = when (sessionState) {
    is SessionState.Unknown -> null
    is SessionState.LoggedIn -> HomeRoute
    is SessionState.LoggedOut -> LoginRoute
}

// ========== 主要Composable ==========

@Composable
fun MainApp(
    viewModel: MainViewModel = hiltViewModel(),
    privacyConsentManager: PrivacyConsentManager? = null
) {
    val context = LocalContext.current
    val consentManager = privacyConsentManager
        ?: (context.applicationContext as MainApplication).privacyConsentManager
    var isConsented by rememberSaveable { mutableStateOf(consentManager.isPrivacyConsented) }

    // 首次启动时显示隐私政策同意弹窗
    if (!isConsented) {
        PrivacyConsentDialog(
            onAgree = {
                consentManager.markConsented()
                (context.applicationContext as? MainApplication)?.performPostConsentInit()
                isConsented = true
            },
            onDisagree = { /* Dialog 内部会 finish Activity */ }
        )
        return
    }

    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val appVersionModel by viewModel.appVersionModel.collectAsStateWithLifecycle()
    val startDestination = resolveStartDestination(sessionState)

    if (startDestination == null) {
        SplashScreen()
    } else {
        AppNavigation(startDestination = startDestination,
            sessionIdentity = sessionState.user?.userId?.toString() ?: "anonymous")
    }

    appVersionModel?.let {
        val updateViewModel: AppUpdateViewModel = hiltViewModel()
        AppUpdateDialog(
            appVersionModel = it,
            viewModel = updateViewModel,
            onDismiss = { viewModel.clearAppVersionModel() }
        )
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AppNavigation(startDestination: AppRoute, sessionIdentity: String = "standalone") {
    AppNavigationHost(startDestination, sessionIdentity) { navigator ->
        AppEntryProviderBuilder().apply { registerAppNavGraphs(navigator) }
    }
}

@Composable
internal fun AppNavigationHost(
    startDestination: AppRoute,
    sessionIdentity: String,
    entries: (AppNavigator) -> AppEntryProviderBuilder,
) {
    // Save the identity alongside the stack. A restored stack must not cross an account boundary.
    var savedIdentity by rememberSaveable { mutableStateOf(sessionIdentity) }
    var epoch by rememberSaveable { mutableStateOf(0) }
    if (savedIdentity != sessionIdentity) {
        savedIdentity = sessionIdentity
        epoch++
    }
    key(epoch) {
        val backStack = rememberNavBackStack(AppNavEntry(startDestination))
        val results = rememberSaveable(saver = NavigationResults.Saver) { NavigationResults() }
        val navigator = remember(backStack, results) { AppNavigator(backStack, results) }
        val registry = remember(navigator) { entries(navigator) }
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.popBackStack() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberHomeViewModelDecorator(navigator)),
            entryProvider = { key ->
                val entry = key as AppNavEntry
                val homeId = when (val route = entry.route) {
                    HomeRoute -> entry.id
                    CarePlansListRoute, ServiceRecordsListRoute -> checkNotNull(entry.homeId)
                    is WebViewRoute -> if (route.isEvaluation) entry.homeId else null
                    else -> null
                }
                NavEntry(key, contentKey = entry.id,
                    metadata = homeId?.let { mapOf("homeOwnerId" to it) } ?: emptyMap()) {
                    registry.Content(entry, navigator)
                }
            },
        )
    }
}
