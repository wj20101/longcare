package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.registerServiceFlowNavGraphs(navController: NavController) {
    registerServiceHoursRoute(navController)
    registerNursingExecutionRoute(navController)
    registerNfcSignInRoute(navController)
    registerSelectServiceRoute(navController)
    registerPhotoUploadRoute(navController)
    registerServiceCountdownRoute(navController)
    registerServiceCompleteRoute(navController)
    registerEndServiceSelectionRoute(navController)
}
