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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.common.utils.toJsonString
import com.ytone.longcare.common.utils.toObject
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.nfcsignin.model.EndOderInfo
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.nfcsignin.ui.NfcSignInScreen
import com.ytone.longcare.features.nfcsignin.ui.SignInMode
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AppDestinations {
    // 路径参数名称常量
    const val ORDER_ID_ARG = "orderId"
    const val SIGN_IN_MODE_ARG = "signInMode"
    const val END_ORDER_PARAMS_ARG = "endOrderParams"

    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val SERVICE_ROUTE = "service/{${ORDER_ID_ARG}}"
    const val NURSING_EXECUTION_ROUTE = "nursing_execution/{${ORDER_ID_ARG}}"
    const val NFC_SIGN_IN_ROUTE = "nfc_sign_in/{${ORDER_ID_ARG}}?${SIGN_IN_MODE_ARG}={${SIGN_IN_MODE_ARG}}&${END_ORDER_PARAMS_ARG}={${END_ORDER_PARAMS_ARG}}"
    const val SELECT_SERVICE_ROUTE = "select_service/{${ORDER_ID_ARG}}"
    const val PHOTO_UPLOAD_ROUTE = "photo_upload/{${ORDER_ID_ARG}}?projectIds={projectIds}"
    const val CARE_PLANS_LIST_ROUTE = "care_plans_list"
    const val SERVICE_RECORDS_LIST_ROUTE = "service_records_list"

    // 路径构建函数
    fun buildServiceRoute(orderId: Long): String {
        return SERVICE_ROUTE.replace("{$ORDER_ID_ARG}", orderId.toString())
    }

    fun buildNursingExecutionRoute(orderId: Long): String {
        return NURSING_EXECUTION_ROUTE.replace("{$ORDER_ID_ARG}", orderId.toString())
    }

    fun buildNfcSignInRoute(orderId: Long, signInMode: SignInMode): String {
        return NFC_SIGN_IN_ROUTE
            .replace("{$ORDER_ID_ARG}", orderId.toString())
            .replace("{$SIGN_IN_MODE_ARG}", signInMode.name)
    }

    fun buildSelectServiceRoute(orderId: Long): String {
        return SELECT_SERVICE_ROUTE.replace("{$ORDER_ID_ARG}", orderId.toString())
    }

    fun buildPhotoUploadRoute(orderId: Long): String {
        return PHOTO_UPLOAD_ROUTE.replace("{$ORDER_ID_ARG}", orderId.toString())
    }
}

fun NavController.navigateToHomeFromLogin() {
    navigate(AppDestinations.HOME_ROUTE) {
        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
    }
}

fun NavController.navigateToService(orderId: Long) {
    navigate(AppDestinations.buildServiceRoute(orderId))
}

fun NavController.navigateToNursingExecution(orderId: Long) {
    navigate(AppDestinations.buildNursingExecutionRoute(orderId))
}

fun NavController.navigateToNfcSignInForStartOrder(orderId: Long) {
    val route = AppDestinations.NFC_SIGN_IN_ROUTE
        .replace("{${AppDestinations.ORDER_ID_ARG}}", orderId.toString())
        .replace("{${AppDestinations.SIGN_IN_MODE_ARG}}", SignInMode.START_ORDER.name)
        .replace("&${AppDestinations.END_ORDER_PARAMS_ARG}={${AppDestinations.END_ORDER_PARAMS_ARG}}", "")
    navigate(route)
}

fun NavController.navigateToNfcSignInForEndOrder(orderId: Long, params: EndOderInfo) {
    val paramsJson = params.toJsonString()
    val route = AppDestinations.NFC_SIGN_IN_ROUTE
        .replace("{${AppDestinations.ORDER_ID_ARG}}", orderId.toString())
        .replace("{${AppDestinations.SIGN_IN_MODE_ARG}}", SignInMode.END_ORDER.name)
        .replace("{${AppDestinations.END_ORDER_PARAMS_ARG}}", URLEncoder.encode(paramsJson, StandardCharsets.UTF_8.name()))
    navigate(route)
}

fun NavController.navigateToCarePlansList() {
    navigate(AppDestinations.CARE_PLANS_LIST_ROUTE)
}

fun NavController.navigateToServiceRecordsList() {
    navigate(AppDestinations.SERVICE_RECORDS_LIST_ROUTE)
}

fun NavController.navigateToSelectService(orderId: Long) {
    navigate(AppDestinations.buildSelectServiceRoute(orderId))
}

fun NavController.navigateToPhotoUpload(orderId: Long, projectIds: List<Int>) {
    val route = AppDestinations.PHOTO_UPLOAD_ROUTE
        .replace("{${AppDestinations.ORDER_ID_ARG}}", orderId.toString())
        .replace("{projectIds}", projectIds.joinToString(","))
    navigate(route)
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
        composable(
            route = AppDestinations.SERVICE_ROUTE,
            arguments = listOf(navArgument(AppDestinations.ORDER_ID_ARG) {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong(AppDestinations.ORDER_ID_ARG) ?: 0L
            ServiceHoursScreen(
                navController = navController,
                orderId = orderId
            )
        }
        composable(
            route = AppDestinations.NURSING_EXECUTION_ROUTE,
            arguments = listOf(navArgument(AppDestinations.ORDER_ID_ARG) {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong(AppDestinations.ORDER_ID_ARG) ?: 0L
            val viewModel: OrderDetailViewModel = hiltViewModel()
            NursingExecutionScreen(
                navController = navController,
                orderId = orderId,
                viewModel = viewModel
            )
        }
        composable(AppDestinations.CARE_PLANS_LIST_ROUTE) {
            ServiceOrdersListScreen(
                navController = navController,
                orderType = ServiceOrderType.PENDING_CARE_PLANS
            )
        }

        composable(AppDestinations.SERVICE_RECORDS_LIST_ROUTE) {
            ServiceOrdersListScreen(
                navController = navController,
                orderType = ServiceOrderType.SERVICE_RECORDS
            )
        }
        composable(
            route = AppDestinations.NFC_SIGN_IN_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.ORDER_ID_ARG) { type = NavType.LongType },
                navArgument(AppDestinations.SIGN_IN_MODE_ARG) {
                    type = NavType.StringType; nullable = true
                },
                navArgument(AppDestinations.END_ORDER_PARAMS_ARG) {
                    type = NavType.StringType; nullable = true
                }
            )
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong(AppDestinations.ORDER_ID_ARG) ?: 0L
            val signInModeName =
                backStackEntry.arguments?.getString(AppDestinations.SIGN_IN_MODE_ARG)
            val signInMode = SignInMode.valueOf(signInModeName ?: SignInMode.START_ORDER.name)
            val paramsJson =
                backStackEntry.arguments?.getString(AppDestinations.END_ORDER_PARAMS_ARG)
            val endOderInfo = paramsJson?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name()).toObject<EndOderInfo>()
            }

            NfcSignInScreen(
                navController = navController,
                orderId = orderId,
                signInMode = signInMode,
                endOderInfo = endOderInfo
            )
        }
        composable(
            route = AppDestinations.SELECT_SERVICE_ROUTE,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0L
            val viewModel: OrderDetailViewModel = hiltViewModel()
            SelectServiceScreen(
                navController = navController,
                orderId = orderId,
                viewModel = viewModel
            )
        }
        composable(
            route = AppDestinations.PHOTO_UPLOAD_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.ORDER_ID_ARG) { type = NavType.LongType },
                navArgument("projectIds") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong(AppDestinations.ORDER_ID_ARG) ?: 0L
            val projectIdsStr = backStackEntry.arguments?.getString("projectIds")
            val projectIds =
                projectIdsStr?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            PhotoUploadScreen(
                navController = navController,
                orderId = orderId,
                projectIds = projectIds
            )
        }
    }
}