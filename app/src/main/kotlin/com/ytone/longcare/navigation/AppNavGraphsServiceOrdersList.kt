package com.ytone.longcare.navigation

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.shared.vm.TodayOrderViewModel

internal fun AppEntryProviderBuilder.registerServiceOrdersListNavGraphs(navController: AppNavigator) {
    destination<CarePlansListRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val parentEntry = LocalHomeViewModelStoreOwner.current
        val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
        ServiceOrdersListScreen(
            actions = ServiceOrdersListActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNursingExecution = { orderKey ->
                    navController.navigateToNursingExecution(orderKey)
                },
                onNavigateToService = { orderKey ->
                    navController.navigateToService(orderKey)
                }
            ),
            orderType = ServiceOrderType.PENDING_CARE_PLANS,
            todayOrderViewModel = todayOrderViewModel
        )
    }

    destination<ServiceRecordsListRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val parentEntry = LocalHomeViewModelStoreOwner.current
        val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
        ServiceOrdersListScreen(
            actions = ServiceOrdersListActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNursingExecution = { orderKey ->
                    navController.navigateToNursingExecution(orderKey)
                },
                onNavigateToService = { orderKey ->
                    navController.navigateToService(orderKey)
                }
            ),
            orderType = ServiceOrderType.SERVICE_RECORDS,
            todayOrderViewModel = todayOrderViewModel
        )
    }
}
