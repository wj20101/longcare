package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.registerSupportNavGraphs(navController: NavController) {
    registerTxFaceRoute(navController)
    registerUserListRoute(navController)
    registerUserServiceRecordRoute(navController)
    registerFaceRecognitionGuideRoute(navController)
    registerIdentificationRoute(navController)
    registerDefaultFaceVerificationRoute(navController)
    registerCameraRoute(navController)
    registerManualFaceCaptureRoute(navController)
    registerWebViewRoute(navController)
}
