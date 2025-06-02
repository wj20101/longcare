package com.ytone.longcare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val SERVICE_ROUTE = "service"
}

fun NavController.navigateToHomeFromLogin() {
    navigate(AppDestinations.HOME_ROUTE) {
        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
    }
}

fun NavController.navigateToService() {
    navigate(AppDestinations.SERVICE_ROUTE)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppDestinations.LOGIN_ROUTE) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(navController = navController)
        }
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(navController = navController)
        }
        composable(AppDestinations.SERVICE_ROUTE){
            ServiceHoursScreen(navController = navController)
        }
    }
}