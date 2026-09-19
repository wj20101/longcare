package com.ytone.longcare.navigation


internal fun AppEntryProviderBuilder.registerServiceFlowNavGraphs(navController: AppNavigator) {
    registerServiceHoursRoute(navController)
    registerNursingExecutionRoute(navController)
    registerNfcSignInRoute(navController)
    registerSelectServiceRoute(navController)
    registerPhotoUploadRoute(navController)
    registerServiceCountdownRoute(navController)
    registerServiceCompleteRoute(navController)
    registerEndServiceSelectionRoute(navController)
}
