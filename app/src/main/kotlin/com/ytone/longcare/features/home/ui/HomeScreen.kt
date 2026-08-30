package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.sales.SalesExperienceScreen
import com.ytone.longcare.navigation.AppEntryState
import com.ytone.longcare.navigation.ReportStartupRootDrawn
import com.ytone.longcare.navigation.StartupRoot
import com.ytone.longcare.navigation.resolveStartupRootReadiness
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.theme.bgGradientBrush

internal enum class HomeExperience {
    Loading,
    Care,
    Sales,
}

internal fun resolveHomeExperience(userIdentity: Int?): HomeExperience = when (userIdentity) {
    null -> HomeExperience.Loading
    2 -> HomeExperience.Sales
    else -> HomeExperience.Care
}

@Composable
internal fun HomeExperienceContent(
    experience: HomeExperience,
    loadingContent: @Composable () -> Unit,
    careContent: @Composable () -> Unit,
    salesContent: @Composable () -> Unit,
) {
    when (experience) {
        HomeExperience.Loading -> loadingContent()
        HomeExperience.Care -> careContent()
        HomeExperience.Sales -> salesContent()
    }
}

@Composable
fun HomeScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel = hiltViewModel(),
    todayOrderViewModel: TodayOrderViewModel = hiltViewModel()
) {
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        homeSharedViewModel.reportHomeEntry()
    }

    HomeExperienceContent(
        experience = resolveHomeExperience(user?.userIdentity),
        salesContent = {
            ReportStartupRootDrawn(
                expectedRoot = StartupRoot.SalesHome,
                actualReadiness = resolveStartupRootReadiness(
                    entryState = AppEntryState.LoggedIn,
                    userIdentity = user?.userIdentity,
                ),
            )
            SalesExperienceScreen(
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
            )
        },
        loadingContent = {
            ReportStartupRootDrawn(
                expectedRoot = StartupRoot.ResolvingSession,
                actualReadiness = resolveStartupRootReadiness(
                    entryState = AppEntryState.LoggedIn,
                    userIdentity = null,
                ),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(bgGradientBrush),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        },
        careContent = {
            ReportStartupRootDrawn(
                expectedRoot = StartupRoot.CareHome,
                actualReadiness = resolveStartupRootReadiness(
                    entryState = AppEntryState.LoggedIn,
                    userIdentity = user?.userIdentity,
                ),
            )
            HomeScreenPagerContent(
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
                todayOrderViewModel = todayOrderViewModel
            )
        },
    )
}
