package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.shared.vm.TodayOrderViewModel

@Composable
fun MainDashboardScreen(
    actions: MainDashboardActions,
    homeSharedViewModel: HomeSharedViewModel,
    todayOrderViewModel: TodayOrderViewModel,
    mainDashboardViewModel: MainDashboardViewModel = hiltViewModel()
) {
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    val companyName by mainDashboardViewModel.companyName.collectAsStateWithLifecycle()

    val todayOrderList by todayOrderViewModel.todayOrderListState.collectAsStateWithLifecycle()
    val inOrderList by todayOrderViewModel.inOrderListState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            todayOrderViewModel.loadTodayOrders()
            todayOrderViewModel.loadInOrders()
        }
    }

    LaunchedEffect(Unit) {
        mainDashboardViewModel.loadCompanyName()
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        user?.let { loggedInUser ->
            MainDashboardContent(
                user = loggedInUser,
                todayOrderList = todayOrderList,
                inOrderList = inOrderList,
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding()
                ),
                companyName = companyName,
                mainDashboardViewModel = mainDashboardViewModel
            )
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun MainDashboardContent(
    user: User,
    todayOrderList: List<TodayServiceOrderModel>,
    inOrderList: List<ServiceOrderModel>,
    actions: MainDashboardActions,
    homeSharedViewModel: HomeSharedViewModel,
    modifier: Modifier = Modifier,
    companyName: String,
    mainDashboardViewModel: MainDashboardViewModel
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            TopHeader(user = user, companyName = companyName)
        }
        item {
            HomeBannerCard()
        }
        item {
            DashboardGridWithImages(
                pendingCarePlanCount = todayOrderList.count { it.state.isPendingCareState() },
                actions = actions
            )
        }
        item {
            OrderTabLayout(
                todayOrderList = todayOrderList,
                inOrderList = inOrderList,
                actions = actions,
                homeSharedViewModel = homeSharedViewModel,
                mainDashboardViewModel = mainDashboardViewModel
            )
        }
    }
}
