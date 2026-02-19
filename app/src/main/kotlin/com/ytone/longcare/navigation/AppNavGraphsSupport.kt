package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.registerSupportNavGraphs(navController: NavController) {
    registerTxFaceRoute(navController)
    registerLocationTrackingRoute()
    registerUserListRoute(navController)
    registerUserServiceRecordRoute(navController)
    registerNfcTestRoute(navController)
    registerFaceRecognitionGuideRoute(navController)
    registerSelectDeviceRoute(navController)
    registerIdentificationRoute(navController)
    registerCameraRoute(navController)
    registerManualFaceCaptureRoute(navController)
    registerWebViewRoute(navController)
}
