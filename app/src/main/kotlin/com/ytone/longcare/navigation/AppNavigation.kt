package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.careplans.ui.CarePlansListScreen
import com.ytone.longcare.features.servicerecords.ui.ServiceRecordsListScreen

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val SERVICE_ROUTE = "service"
    const val CARE_PLANS_LIST_ROUTE = "care_plans_list"
    const val SERVICE_RECORDS_LIST_ROUTE = "service_records_list"
}

fun NavController.navigateToHomeFromLogin() {
    navigate(AppDestinations.HOME_ROUTE) {
        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
    }
}

fun NavController.navigateToService() {
    navigate(AppDestinations.SERVICE_ROUTE)
}

fun NavController.navigateToCarePlansList() {
    navigate(AppDestinations.CARE_PLANS_LIST_ROUTE)
}

fun NavController.navigateToServiceRecordsList() {
    navigate(AppDestinations.SERVICE_RECORDS_LIST_ROUTE)
}

/**
 * 应用的顶层 Composable，负责根据认证状态决定初始导航。
 */
@Composable
fun MainApp(
    viewModel: MainViewModel = hiltViewModel()
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    when (sessionState) {
        is SessionState.Unknown -> {
            // 正在从 DataStore 读取信息，显示启动页或加载动画
            SplashScreen()
        }

        is SessionState.LoggedIn -> {
            // 用户已登录，导航到主页
            AppNavigation(startDestination = AppDestinations.HOME_ROUTE)
        }

        is SessionState.LoggedOut -> {
            // 用户未登录，导航到登录页
            AppNavigation(startDestination = AppDestinations.LOGIN_ROUTE)
        }
    }
}

/**
 * 启动时的加载界面
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(navController = navController)
        }
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(navController = navController)
        }
        composable(AppDestinations.SERVICE_ROUTE) {
            ServiceHoursScreen(navController = navController)
        }
        composable(AppDestinations.CARE_PLANS_LIST_ROUTE) {
            CarePlansListScreen(navController = navController)
        }
        composable(AppDestinations.SERVICE_RECORDS_LIST_ROUTE) {
            ServiceRecordsListScreen(navController = navController)
        }
    }
}