package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.photoupload.model.WatermarkData

internal fun NavGraphBuilder.registerEntryNavGraphs(navController: NavController) {
    composable<LoginRoute> {
        LoginScreen(
            actions = LoginFeatureActions(
                onLoginSuccess = { navController.navigateToHomeFromLogin() },
                onOpenWebPage = { url, title -> navController.navigateToWebView(url, title) },
                onOpenNfcTest = { navController.navigateToNfcTest() },
                onOpenCameraTest = {
                    val mockWatermarkData = WatermarkData(
                        title = "服务前",
                        insuredPerson = "张三",
                        caregiver = "李四",
                        address = "北京市朝阳区xx路xx号"
                    )
                    navController.navigateToCamera(mockWatermarkData)
                },
                onOpenFaceVerificationTest = { navController.navigateToFaceVerificationWithAutoSign() },
                onOpenManualFaceCapture = { navController.navigateToManualFaceCapture() }
            )
        )
    }

    composable<HomeRoute> {
        HomeScreen(
            actions = HomeActions(
                onNavigateToCarePlansList = { navController.navigateToCarePlansList() },
                onNavigateToServiceRecordsList = { navController.navigateToServiceRecordsList() },
                onNavigateToNursingExecution = { orderParams ->
                    navController.navigateToNursingExecution(orderParams)
                },
                onNavigateToService = { orderParams ->
                    navController.navigateToService(orderParams)
                },
                onNavigateToServiceCountdown = { orderParams, projectIdList ->
                    navController.navigateToServiceCountdown(orderParams, projectIdList)
                },
                onNavigateToHaveServiceUserList = { navController.navigateToHaveServiceUserList() },
                onNavigateToNoServiceUserList = { navController.navigateToNoServiceUserList() }
            )
        )
    }
}
