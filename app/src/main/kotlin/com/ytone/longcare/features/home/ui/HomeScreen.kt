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
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.theme.bgGradientBrush

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

    when (user?.userIdentity) {
        2 ->
            SalesExperienceScreen(
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
            )

        null ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(bgGradientBrush),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

        else ->
            HomeScreenPagerContent(
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
                todayOrderViewModel = todayOrderViewModel
            )
    }
}
