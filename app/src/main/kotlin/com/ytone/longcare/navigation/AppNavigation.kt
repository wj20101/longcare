package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.feature.home.FeatureEntry as HomeFeatureEntry
import com.ytone.longcare.feature.identification.FeatureEntry as IdentificationFeatureEntry
import com.ytone.longcare.feature.login.FeatureEntry as LoginFeatureEntry
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel

private val featureRouteRegistry = setOf(
    LoginFeatureEntry.ROUTE,
    HomeFeatureEntry.ROUTE,
    IdentificationFeatureEntry.ROUTE
)

private fun resolveStartDestination(sessionState: SessionState): Any = when (sessionState) {
    is SessionState.Unknown -> SplashRoute
    is SessionState.LoggedIn -> HomeGraphRoute
    is SessionState.LoggedOut -> LoginRoute
}

// ========== 主要Composable ==========

@Composable
fun MainApp(viewModel: MainViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val appVersionModel by viewModel.appVersionModel.collectAsStateWithLifecycle()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val startDestination = resolveStartDestination(sessionState)

    if (startDestination == SplashRoute) {
        SplashScreen()
    } else {
        AppNavigation(startDestination = startDestination)
    }

    appVersionModel?.let {
        AppUpdateDialog(
            appVersionModel = it,
            viewModel = updateViewModel,
            onDismiss = { viewModel.clearAppVersionModel() }
        )
    }
}

private object SplashRoute

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
fun AppNavigation(startDestination: Any) {
    check(featureRouteRegistry.size == 3) {
        "Feature route registry is incomplete."
    }
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        registerAppNavGraphs(navController)
    }
}
