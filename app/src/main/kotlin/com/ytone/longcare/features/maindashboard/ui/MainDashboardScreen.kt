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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.home.vm.HomeDeviceViewModel
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserIdentityDisplay
import com.ytone.longcare.model.userIdentityDisplay
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.isPendingCareState
import com.ytone.longcare.shared.vm.TodayOrderViewModel

@Composable
fun MainDashboardScreen(
    actions: MainDashboardActions,
    homeSharedViewModel: HomeSharedViewModel,
    todayOrderViewModel: TodayOrderViewModel,
    mainDashboardViewModel: MainDashboardViewModel = hiltViewModel(),
    isHomeTabSelected: Boolean = true,
    homeDeviceViewModel: HomeDeviceViewModel = hiltViewModel(),
) {
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    val companyName by mainDashboardViewModel.companyName.collectAsStateWithLifecycle()
    val deviceStatus by homeDeviceViewModel.deviceStatus.collectAsStateWithLifecycle()

    val todayOrderList by todayOrderViewModel.todayOrderListState.collectAsStateWithLifecycle()
    val inOrderList by todayOrderViewModel.inOrderListState.collectAsStateWithLifecycle()
    val orderMessages by todayOrderViewModel.uiMessages.collectAsStateWithLifecycle()
    val dashboardMessages by mainDashboardViewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current

    HomeDevicePollingEffect(homeDeviceViewModel, isHomeTabSelected, lifecycleOwner)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            todayOrderViewModel.loadTodayOrders()
            todayOrderViewModel.loadInOrders()
        }
    }

    LaunchedEffect(Unit) {
        mainDashboardViewModel.loadCompanyName()
    }

    UiMessageSnackbarEffect(
        messages = orderMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = todayOrderViewModel::consumeUiMessage,
    )
    UiMessageSnackbarEffect(
        messages = dashboardMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = mainDashboardViewModel::consumeUiMessage,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                mainDashboardViewModel = mainDashboardViewModel,
                deviceStatus = deviceStatus,
            )
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
internal fun HomeDevicePollingEffect(
    viewModel: HomeDeviceViewModel,
    isHomeTabSelected: Boolean,
    lifecycleOwner: LifecycleOwner,
) {
    LaunchedEffect(lifecycleOwner, isHomeTabSelected, viewModel) {
        if (isHomeTabSelected) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pollDeviceStatus()
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
    mainDashboardViewModel: MainDashboardViewModel,
    deviceStatus: ZhiChiDeviceStatusModel?,
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
        if (user.userIdentityDisplay() != UserIdentityDisplay.SALES && deviceStatus != null) {
            item {
                HomeDeviceStatusCard(deviceStatus, actions.onOpenDevicePage)
            }
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
