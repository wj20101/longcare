package com.ytone.longcare.navigation

import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.common.utils.findBackStackEntryOrNull
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.shared.vm.TodayOrderViewModel

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

    navigation<HomeGraphRoute>(startDestination = HomeRoute) {
        composable<HomeRoute> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.findBackStackEntryOrNull(HomeGraphRoute) ?: backStackEntry
            }
            val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
            HomeScreen(
                actions = HomeActions(
                    onNavigateToCarePlansList = { navController.navigateToCarePlansList() },
                    onNavigateToServiceRecordsList = { navController.navigateToServiceRecordsList() },
                    onNavigateToNursingExecution = { orderKey ->
                        navController.navigateToNursingExecution(orderKey)
                    },
                    onNavigateToService = { orderKey ->
                        navController.navigateToService(orderKey)
                    },
                    onNavigateToServiceCountdown = { orderKey, projectIdList ->
                        navController.navigateToServiceCountdown(orderKey, projectIdList)
                    },
                    onNavigateToHaveServiceUserList = { navController.navigateToHaveServiceUserList() },
                    onNavigateToNoServiceUserList = { navController.navigateToNoServiceUserList() }
                ),
                todayOrderViewModel = todayOrderViewModel
            )
        }

        registerServiceOrdersListNavGraphs(navController)
    }
}
