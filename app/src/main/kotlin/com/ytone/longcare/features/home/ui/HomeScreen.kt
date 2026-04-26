package com.ytone.longcare.features.home.ui

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.shared.vm.TodayOrderViewModel

@Composable
fun HomeScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel = hiltViewModel(),
    todayOrderViewModel: TodayOrderViewModel = hiltViewModel()
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    LaunchedEffect(Unit) {
        homeSharedViewModel.reportHomeEntry()
    }

    HomeScreenPagerContent(
        actions = actions,
        homeSharedViewModel = homeSharedViewModel,
        todayOrderViewModel = todayOrderViewModel
    )
}
