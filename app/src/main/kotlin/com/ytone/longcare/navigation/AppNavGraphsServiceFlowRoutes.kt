package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ytone.longcare.features.endservice.api.EndServiceSelectionActions
import com.ytone.longcare.features.endservice.ui.EndServiceSelectionScreen
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.ui.NfcWorkflowScreen
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.servicecomplete.api.ServiceCompleteActions
import com.ytone.longcare.features.servicecomplete.ui.ServiceCompleteScreen
import com.ytone.longcare.features.servicehours.api.ServiceHoursActions
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import kotlin.reflect.typeOf

internal fun NavGraphBuilder.registerServiceHoursRoute(navController: NavController) {
    composable<ServiceRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<ServiceRoute>()
        ServiceHoursScreen(
            actions = ServiceHoursActions(
                onNavigateBack = { navController.popBackStack() }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerNursingExecutionRoute(navController: NavController) {
    composable<NursingExecutionRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<NursingExecutionRoute>()
        NursingExecutionScreen(
            actions = NursingExecutionActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(orderKey, projectIdList)
                },
                onNavigateToSelectDevice = { orderKey ->
                    navController.navigateToSelectDevice(orderKey)
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerNfcSignInRoute(navController: NavController) {
    composable<NfcSignInRoute>(
        typeMap = mapOf(
            typeOf<EndOderInfo?>() to EndOderInfoNavType,
            typeOf<OrderNavParams>() to OrderNavParamsNavType
        )
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<NfcSignInRoute>()
        NfcWorkflowScreen(
            actions = NfcWorkflowActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() },
                onNavigateToIdentification = { orderKey ->
                    navController.navigateToIdentification(orderKey)
                },
                onNavigateToServiceComplete = { orderKey, serviceCompleteData ->
                    navController.navigateToServiceComplete(
                        orderKey = orderKey,
                        serviceCompleteData = serviceCompleteData
                    )
                }
            ),
            orderKey = route.orderParams.toOrderKey(),
            signInMode = route.signInMode,
            endOderInfo = route.endOrderParams
        )
    }
}

internal fun NavGraphBuilder.registerSelectServiceRoute(navController: NavController) {
    composable<SelectServiceRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SelectServiceRoute>()
        SelectServiceScreen(
            actions = SelectServiceActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(
                        orderKey = orderKey,
                        projectIdList = projectIdList
                    )
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerServiceCompleteRoute(navController: NavController) {
    composable<ServiceCompleteRoute>(
        typeMap = mapOf(
            typeOf<ServiceCompleteData>() to ServiceCompleteDataNavType,
            typeOf<OrderNavParams>() to OrderNavParamsNavType
        )
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<ServiceCompleteRoute>()
        ServiceCompleteScreen(
            actions = ServiceCompleteActions(
                onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() }
            ),
            orderKey = route.orderParams.toOrderKey(),
            serviceCompleteData = route.serviceCompleteData
        )
    }
}

internal fun NavGraphBuilder.registerEndServiceSelectionRoute(navController: NavController) {
    composable<EndServiceSelectionRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<EndServiceSelectionRoute>()
        EndServiceSelectionScreen(
            actions = EndServiceSelectionActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNfcSignInForEndOrder = { orderKey, params ->
                    navController.navigateToNfcSignInForEndOrder(orderKey, params)
                }
            ),
            orderKey = route.orderParams.toOrderKey(),
            endType = route.endType,
            initialProjectIdList = route.initialProjectIdList
        )
    }
}
