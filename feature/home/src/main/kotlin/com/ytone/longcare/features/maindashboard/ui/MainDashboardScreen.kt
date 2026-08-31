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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.core.ui.header.TopHeader
import com.ytone.longcare.features.home.api.HomeOrderStateSource
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.isPendingCareState

@Composable
internal fun MainDashboardScreen(
    user: CurrentUser,
    selectedTabIndex: Int,
    onSelectedTab: (Int) -> Unit,
    actions: MainDashboardActions,
    orderStateSource: HomeOrderStateSource,
    mainDashboardViewModel: MainDashboardViewModel = hiltViewModel()
) {
    val companyName by mainDashboardViewModel.companyName.collectAsStateWithLifecycle()

    val todayOrderList by orderStateSource.todayOrders.collectAsStateWithLifecycle()
    val inOrderList by orderStateSource.inProgressOrders.collectAsStateWithLifecycle()
    val orderMessages by orderStateSource.messages.collectAsStateWithLifecycle()
    val dashboardMessages by mainDashboardViewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            orderStateSource.refreshTodayOrders()
            orderStateSource.refreshInProgressOrders()
        }
    }

    LaunchedEffect(Unit) {
        mainDashboardViewModel.loadCompanyName()
    }

    UiMessageSnackbarEffect(
        messages = orderMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = orderStateSource::consumeMessage,
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
        MainDashboardContent(
                user = user,
                todayOrderList = todayOrderList,
                inOrderList = inOrderList,
                actions = actions,
                selectedTabIndex = selectedTabIndex,
                onSelectedTab = onSelectedTab,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding()
                ),
                companyName = companyName,
                mainDashboardViewModel = mainDashboardViewModel
            )
    }
}

@Composable
private fun MainDashboardContent(
    user: CurrentUser,
    todayOrderList: List<TodayServiceOrderModel>,
    inOrderList: List<ServiceOrderModel>,
    actions: MainDashboardActions,
    selectedTabIndex: Int,
    onSelectedTab: (Int) -> Unit,
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
                selectedTabIndex = selectedTabIndex,
                onSelectedTab = onSelectedTab,
                mainDashboardViewModel = mainDashboardViewModel
            )
        }
    }
}
