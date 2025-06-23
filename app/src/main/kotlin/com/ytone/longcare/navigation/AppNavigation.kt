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
import androidx.navigation.toRoute
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.nfcsignin.ui.NfcSignInScreen
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import kotlin.reflect.typeOf


fun NavController.navigateToHomeFromLogin() {
    navigate(HomeRoute) {
        popUpTo(LoginRoute) { inclusive = true }
    }
}

fun NavController.navigateToService(orderId: Long) {
    navigate(ServiceRoute(orderId))
}

fun NavController.navigateToNursingExecution(orderId: Long) {
    navigate(NursingExecutionRoute(orderId))
}

fun NavController.navigateToNfcSignInForStartOrder(orderId: Long) {
    navigate(NfcSignInRoute(orderId = orderId, signInMode = SignInMode.START_ORDER))
}

fun NavController.navigateToNfcSignInForEndOrder(orderId: Long, params: EndOderInfo) {
    navigate(NfcSignInRoute(orderId = orderId, signInMode = SignInMode.END_ORDER, endOrderParams = params))
}

fun NavController.navigateToCarePlansList() {
    navigate(CarePlansListRoute)
}

fun NavController.navigateToServiceRecordsList() {
    navigate(ServiceRecordsListRoute)
}

fun NavController.navigateToSelectService(orderId: Long) {
    navigate(SelectServiceRoute(orderId))
}

fun NavController.navigateToPhotoUpload(orderId: Long, projectIds: List<Int>) {
    navigate(PhotoUploadRoute(orderId, projectIds))
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
            AppNavigation(startDestination = HomeRoute)
        }

        is SessionState.LoggedOut -> {
            // 用户未登录，导航到登录页
            AppNavigation(startDestination = LoginRoute)
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
fun AppNavigation(startDestination: Any) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable<LoginRoute> {
            LoginScreen(navController = navController)
        }
        composable<HomeRoute> {
            HomeScreen(navController = navController)
        }
        composable<ServiceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceRoute>()
            ServiceHoursScreen(
                navController = navController,
                orderId = route.orderId
            )
        }
        composable<NursingExecutionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<NursingExecutionRoute>()
            val viewModel: OrderDetailViewModel = hiltViewModel()
            NursingExecutionScreen(
                navController = navController,
                orderId = route.orderId,
                viewModel = viewModel
            )
        }
        composable<CarePlansListRoute> {
            ServiceOrdersListScreen(
                navController = navController,
                orderType = ServiceOrderType.PENDING_CARE_PLANS
            )
        }

        composable<ServiceRecordsListRoute> {
            ServiceOrdersListScreen(
                navController = navController,
                orderType = ServiceOrderType.SERVICE_RECORDS
            )
        }
        composable<NfcSignInRoute>(
            typeMap = mapOf(typeOf<EndOderInfo?>() to EndOderInfoNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NfcSignInRoute>()
            
            NfcSignInScreen(
                navController = navController,
                orderId = route.orderId,
                signInMode = route.signInMode,
                endOderInfo = route.endOrderParams
            )
        }
        composable<SelectServiceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SelectServiceRoute>()
            val viewModel: OrderDetailViewModel = hiltViewModel()
            SelectServiceScreen(
                navController = navController,
                orderId = route.orderId,
                viewModel = viewModel
            )
        }
        composable<PhotoUploadRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PhotoUploadRoute>()
            PhotoUploadScreen(
                navController = navController,
                orderId = route.orderId,
                projectIds = route.projectIds
            )
        }
    }
}