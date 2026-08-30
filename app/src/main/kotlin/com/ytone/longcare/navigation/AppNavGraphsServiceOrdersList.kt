package com.ytone.longcare.navigation

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.shared.vm.TodayOrderViewModel

internal fun NavGraphBuilder.registerServiceOrdersListNavGraphs(navController: NavController) {
    composable<CarePlansListRoute> { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.requireHomeGraphBackStackEntry()
        }
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

    composable<ServiceRecordsListRoute> { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.requireHomeGraphBackStackEntry()
        }
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
