package com.ytone.longcare.navigation

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

internal fun AppEntryProviderBuilder.registerServiceHoursRoute(navController: AppNavigator) {
    destination<ServiceRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<ServiceRoute>()
        ServiceHoursScreen(
            actions = ServiceHoursActions(
                onNavigateBack = { navController.popBackStack() }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun AppEntryProviderBuilder.registerNursingExecutionRoute(navController: AppNavigator) {
    destination<NursingExecutionRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<NursingExecutionRoute>()
        NursingExecutionScreen(
            actions = NursingExecutionActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(orderKey, projectIdList)
                },
                onStartOrderNfcSignIn = { orderKey ->
                    navController.navigateToNfcSignInForStartOrder(orderKey)
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun AppEntryProviderBuilder.registerNfcSignInRoute(navController: AppNavigator) {
    destination<NfcSignInRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<NfcSignInRoute>()
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

internal fun AppEntryProviderBuilder.registerSelectServiceRoute(navController: AppNavigator) {
    destination<SelectServiceRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<SelectServiceRoute>()
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

internal fun AppEntryProviderBuilder.registerServiceCompleteRoute(navController: AppNavigator) {
    destination<ServiceCompleteRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<ServiceCompleteRoute>()
        ServiceCompleteScreen(
            actions = ServiceCompleteActions(
                onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() }
            ),
            orderKey = route.orderParams.toOrderKey(),
            serviceCompleteData = route.serviceCompleteData
        )
    }
}

internal fun AppEntryProviderBuilder.registerEndServiceSelectionRoute(navController: AppNavigator) {
    destination<EndServiceSelectionRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<EndServiceSelectionRoute>()
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
