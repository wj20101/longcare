package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.core.ui.navigation.AdaptiveAppNavigationScaffold
import com.ytone.longcare.core.ui.navigation.AppNavigationItem
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.api.HomeFeatureConfig
import com.ytone.longcare.features.home.api.HomeOrderStateSource
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.model.CurrentUser
import kotlinx.coroutines.launch

@Composable
internal fun HomeCareContent(
    actions: HomeActions,
    config: HomeFeatureConfig,
    user: CurrentUser,
    selectedDashboardTab: Int,
    onSelectedDashboardTab: (Int) -> Unit,
    orderStateSource: HomeOrderStateSource,
) {
    val navigationItems = listOf(
        AppNavigationItem(
            text = stringResource(R.string.home_navigation_home),
            testTag = "home_navigation_dashboard",
        ),
        AppNavigationItem(
            text = stringResource(R.string.home_navigation_nursing),
            testTag = "home_navigation_nursing",
        ),
        AppNavigationItem(
            text = stringResource(R.string.home_navigation_profile),
            testTag = "home_navigation_profile",
        ),
    )
    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_care_home_root")
            .background(brush = bgGradientBrush)
    ) {
        AdaptiveAppNavigationScaffold(
            items = navigationItems,
            selectedItemIndex = pagerState.currentPage,
            onItemSelected = {
                coroutineScope.launch { pagerState.scrollToPage(it) }
            },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> MainDashboardScreen(
                        actions = MainDashboardActions(
                            onNavigateToCarePlansList = actions.onNavigateToCarePlansList,
                            onNavigateToServiceRecordsList = actions.onNavigateToServiceRecordsList,
                            onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                            onNavigateToService = actions.onNavigateToService,
                            onNavigateToServiceCountdown = actions.onNavigateToServiceCountdown
                        ),
                        user = user,
                        selectedTabIndex = selectedDashboardTab,
                        onSelectedTab = onSelectedDashboardTab,
                        orderStateSource = orderStateSource,
                    )

                    1 -> NursingScreen(
                        actions = NursingActions(
                            onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                            onNavigateToService = actions.onNavigateToService
                        )
                    )

                    2 -> HomeProfileContent(user = user, config = config, actions = actions)
                }
            }
        }
    }
}

@Composable
internal fun HomeProfileContent(
    user: CurrentUser,
    config: HomeFeatureConfig,
    actions: HomeActions,
) {
    ProfileScreen(
        user = user,
        config = config,
        actions = ProfileActions(
            onNavigateToHaveServiceUserList = actions.onNavigateToHaveServiceUserList,
            onNavigateToNoServiceUserList = actions.onNavigateToNoServiceUserList,
            onOpenUserAgreement = actions.onOpenUserAgreement,
            onOpenPrivacyPolicy = actions.onOpenPrivacyPolicy,
        ),
    )
}
