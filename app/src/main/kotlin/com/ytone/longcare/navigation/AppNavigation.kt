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
import com.ytone.longcare.features.location.ui.LocationTrackingScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownScreen
import com.ytone.longcare.features.nfc.ui.NfcWorkflowScreen
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.features.servicecomplete.ui.ServiceCompleteScreen
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.selectdevice.ui.SelectDeviceScreen
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import kotlin.reflect.typeOf

/**
 * 从登录页面导航到主页，并清除登录页面的返回栈
 * @param NavController 导航控制器
 */
fun NavController.navigateToHomeFromLogin() {
    navigate(HomeRoute) {
        popUpTo(LoginRoute) { inclusive = true }
    }
}

/**
 * 导航到服务详情页面
 * @param orderId 订单ID
 */
fun NavController.navigateToService(orderId: Long) {
    navigate(ServiceRoute(orderId))
}

/**
 * 导航到护理执行页面
 * @param orderId 订单ID
 */
fun NavController.navigateToNursingExecution(orderId: Long) {
    navigate(NursingExecutionRoute(orderId))
}

/**
 * 导航到NFC签到页面（开始订单模式）
 * @param orderId 订单ID
 */
fun NavController.navigateToNfcSignInForStartOrder(orderId: Long) {
    navigate(NfcSignInRoute(orderId = orderId, signInMode = SignInMode.START_ORDER))
}

/**
 * 导航到NFC签到页面（结束订单模式）
 * @param orderId 订单ID
 * @param params 结束订单的信息参数
 */
fun NavController.navigateToNfcSignInForEndOrder(orderId: Long, params: EndOderInfo) {
    navigate(
        NfcSignInRoute(
            orderId = orderId,
            signInMode = SignInMode.END_ORDER,
            endOrderParams = params
        )
    )
}

/**
 * 导航到护理计划列表页面
 */
fun NavController.navigateToCarePlansList() {
    navigate(CarePlansListRoute)
}

/**
 * 导航到服务记录列表页面
 */
fun NavController.navigateToServiceRecordsList() {
    navigate(ServiceRecordsListRoute)
}

/**
 * 导航到选择服务页面
 * @param orderId 订单ID
 */
fun NavController.navigateToSelectService(orderId: Long) {
    navigate(SelectServiceRoute(orderId))
}

/**
 * 导航到照片上传页面
 * @param orderId 订单ID
 */
fun NavController.navigateToPhotoUpload(orderId: Long) {
    navigate(PhotoUploadRoute(orderId))
}

/**
 * 导航到服务倒计时页面
 * @param orderId 订单ID
 * @param projectIdList 项目ID列表
 */
fun NavController.navigateToServiceCountdown(
    orderId: Long,
    projectIdList: List<Int>
) {
    navigate(ServiceCountdownRoute(orderId, projectIdList))
}

/**
 * 导航到服务完成页面
 * @param orderId 订单ID
 */
fun NavController.navigateToServiceComplete(orderId: Long) {
    navigate(ServiceCompleteRoute(orderId))
}

/**
 * 导航到人脸识别引导页面
 */
fun NavController.navigateToFaceRecognitionGuide(orderId: Long) {
    navigate(FaceRecognitionGuideRoute(orderId = orderId))
}

/**
 * 导航到选择设备页面
 * @param orderId 订单ID
 */
fun NavController.navigateToSelectDevice(orderId: Long) {
    navigate(SelectDeviceRoute(orderId))
}

/**
 * 导航到身份认证页面
 * @param orderId 订单ID
 */
fun NavController.navigateToIdentification(orderId: Long) {
    navigate(IdentificationRoute(orderId))
}

/**
 * 导航到已服务工时用户列表页面
 */
fun NavController.navigateToHaveServiceUserList() {
    navigate(HaveServiceUserListRoute)
}

/**
 * 导航到未服务工时用户列表页面
 */
fun NavController.navigateToNoServiceUserList() {
    navigate(NoServiceUserListRoute)
}

/**
 * 导航到主页并清除所有返回栈
 */
fun NavController.navigateToHomeAndClearStack() {
    navigate(HomeRoute) {
        popUpTo(0) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * 应用的顶层 Composable，负责根据认证状态决定初始导航。
 * @param viewModel 主要的ViewModel，用于获取会话状态
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

/**
 * 应用的主要导航组件
 * @param startDestination 初始导航目的地
 */
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
            NursingExecutionScreen(
                navController = navController,
                orderId = route.orderId
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

            NfcWorkflowScreen(
                navController = navController,
                orderId = route.orderId,
                signInMode = route.signInMode,
                endOderInfo = route.endOrderParams
            )
        }
        composable<SelectServiceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SelectServiceRoute>()
            SelectServiceScreen(
                navController = navController,
                orderId = route.orderId
            )
        }
        composable<PhotoUploadRoute>(
            typeMap = mapOf(typeOf<EndOderInfo?>() to EndOderInfoNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<PhotoUploadRoute>()
            PhotoUploadScreen(
                navController = navController,
                orderId = route.orderId
            )
        }
        composable<ServiceCountdownRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCountdownRoute>()
            ServiceCountdownScreen(
                navController = navController,
                orderId = route.orderId,
                projectIdList = route.projectIdList
            )
        }
        composable<TxFaceRoute> { backStackEntry ->
            FaceVerificationWithAutoSignScreen(
                navController = navController,
                {},
                {},
            )
        }
        composable<LocationTrackingRoute> { backStackEntry ->
            LocationTrackingScreen()
        }
        composable<ServiceCompleteRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCompleteRoute>()
            ServiceCompleteScreen(navController = navController, orderId = route.orderId)
        }

        composable<FaceRecognitionGuideRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FaceRecognitionGuideRoute>()
            FaceRecognitionGuideScreen(navController = navController, orderId = route.orderId)
        }
        
        composable<SelectDeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SelectDeviceRoute>()
            SelectDeviceScreen(
                navController = navController,
                orderId = route.orderId
            )
        }
        
        composable<IdentificationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<IdentificationRoute>()
            IdentificationScreen(
                navController = navController,
                orderId = route.orderId
            )
        }
        
        composable<HaveServiceUserListRoute> {
            UserListScreen(
                navController = navController,
                userListType = UserListType.HAVE_SERVICE
            )
        }
        
        composable<NoServiceUserListRoute> {
            UserListScreen(
                navController = navController,
                userListType = UserListType.NO_SERVICE
            )
        }
    }
}