package com.ytone.longcare.navigation


internal fun AppEntryProviderBuilder.registerSupportNavGraphs(navController: AppNavigator) {
    registerUserListRoute(navController)
    registerUserServiceRecordRoute(navController)
    registerIdentificationRoute(navController)
    registerDefaultFaceVerificationRoute(navController)
    registerCameraRoute(navController)
    registerManualFaceCaptureRoute(navController)
    registerWebViewRoute(navController)
}
