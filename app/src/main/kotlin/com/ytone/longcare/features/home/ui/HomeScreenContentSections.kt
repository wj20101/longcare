package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.theme.bgGradientBrush
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreenPagerContent(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel,
    todayOrderViewModel: TodayOrderViewModel
) {
    val navigationItems = listOf(
        AppNavigationItem("首页"),
        AppNavigationItem("护理工作"),
        AppNavigationItem("我的")
    )
    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        homeSharedViewModel = homeSharedViewModel,
                        todayOrderViewModel = todayOrderViewModel
                    )

                    1 -> NursingScreen(
                        actions = NursingActions(
                            onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                            onNavigateToService = actions.onNavigateToService
                        )
                    )

                    2 -> ProfileScreen(
                        actions = ProfileActions(
                            onNavigateToHaveServiceUserList = actions.onNavigateToHaveServiceUserList,
                            onNavigateToNoServiceUserList = actions.onNavigateToNoServiceUserList,
                            onOpenUserAgreement = actions.onOpenUserAgreement,
                            onOpenPrivacyPolicy = actions.onOpenPrivacyPolicy
                        ),
                        homeSharedViewModel = homeSharedViewModel
                    )
                }
            }
        }
    }
}
