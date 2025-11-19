package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.di.SelectedProjectsManagerEntryPoint
import com.ytone.longcare.api.request.OrderInfoRequestModel
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
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.features.servicecomplete.ui.ServiceCompleteScreen
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.selectdevice.ui.SelectDeviceScreen
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.userservicerecord.ui.UserServiceRecordScreen
import com.ytone.longcare.features.nfctest.ui.NfcTestScreen
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.core.navigation.NavigationConstants
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
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToService(orderInfoRequest: OrderInfoRequestModel) {
    navigate(ServiceRoute(orderInfoRequest))
}

/**
 * 导航到护理执行页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToNursingExecution(orderInfoRequest: OrderInfoRequestModel) {
    navigate(NursingExecutionRoute(orderInfoRequest))
}

/**
 * 导航到NFC签到页面（开始订单模式）
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToNfcSignInForStartOrder(orderInfoRequest: OrderInfoRequestModel) {
    navigate(NfcSignInRoute(orderInfoRequest = orderInfoRequest, signInMode = SignInMode.START_ORDER))
}

/**
 * 导航到NFC签到页面（结束订单模式）
 * @param orderInfoRequest 订单信息请求模型
 * @param params 结束订单的信息参数
 */
fun NavController.navigateToNfcSignInForEndOrder(orderInfoRequest: OrderInfoRequestModel, params: EndOderInfo) {
    navigate(
        NfcSignInRoute(
            orderInfoRequest = orderInfoRequest,
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
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToSelectService(orderInfoRequest: OrderInfoRequestModel) {
    navigate(SelectServiceRoute(orderInfoRequest))
}

/**
 * 导航到照片上传页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToPhotoUpload(orderInfoRequest: OrderInfoRequestModel) {
    navigate(PhotoUploadRoute(orderInfoRequest))
}

/**
 * 导航到服务倒计时页面
 * @param orderInfoRequest 订单信息请求模型
 * @param projectIdList 项目ID列表
 */
fun NavController.navigateToServiceCountdown(
    orderInfoRequest: OrderInfoRequestModel,
    projectIdList: List<Int>
) {
    navigate(ServiceCountdownRoute(orderInfoRequest, projectIdList))
}

/**
 * 导航到服务完成页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToServiceComplete(orderInfoRequest: OrderInfoRequestModel) {
    // 获取当前页面的路由，以便之后将其弹出
    val currentRoute = this.currentBackStackEntry?.destination?.route ?: return

    navigate(ServiceCompleteRoute(orderInfoRequest)) {
        // popUpTo 会从返回堆栈中移除目标路由（及之上）的所有页面
        popUpTo(currentRoute) {
            inclusive = true // inclusive = true 表示连同 currentRoute 页面本身也一起移除
        }

        // (可选但推荐) 防止在快速点击时重复创建页面
        launchSingleTop = true
    }
}

/**
 * 导航到人脸识别引导页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToFaceRecognitionGuide(orderInfoRequest: OrderInfoRequestModel) {
    navigate(FaceRecognitionGuideRoute(orderInfoRequest = orderInfoRequest))
}

/**
 * 导航到选择设备页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToSelectDevice(orderInfoRequest: OrderInfoRequestModel) {
    navigateToNfcSignInForStartOrder(orderInfoRequest)
    // 暂时先去掉 设备选择页
//    navigate(SelectDeviceRoute(orderInfoRequest))
}

/**
 * 导航到身份认证页面
 * @param orderInfoRequest 订单信息请求模型
 */
fun NavController.navigateToIdentification(orderInfoRequest: OrderInfoRequestModel) {
    // 获取当前页面的路由，以便之后将其弹出
    val currentRoute = this.currentBackStackEntry?.destination?.route ?: return

    navigate(IdentificationRoute(orderInfoRequest)) {
        // popUpTo 会从返回堆栈中移除目标路由（及之上）的所有页面
        popUpTo(currentRoute) {
            inclusive = true // inclusive = true 表示连同 currentRoute 页面本身也一起移除
        }

        // (可选但推荐) 防止在快速点击时重复创建页面
        launchSingleTop = true
    }
}

/**
 * 导航到用户列表页面
 * @param listType 列表类型
 */
fun NavController.navigateToUserList(listType: String) {
    navigate(UserListRoute(listType))
}

/**
 * 导航到已服务工时用户列表页面
 */
fun NavController.navigateToHaveServiceUserList() {
    navigateToUserList(UserListType.HAVE_SERVICE.name)
}

/**
 * 导航到未服务工时用户列表页面
 */
fun NavController.navigateToNoServiceUserList() {
    navigateToUserList(UserListType.NO_SERVICE.name)
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
 * 导航到用户服务记录页面
 * @param userId 用户ID
 * @param userName 用户昵称
 * @param userAddress 用户地址
 */
fun NavController.navigateToUserServiceRecord(userId: Long, userName: String, userAddress: String) {
    navigate(UserServiceRecordRoute(userId, userName, userAddress))
}

/**
 * 导航到NFC测试页面
 */
fun NavController.navigateToNfcTest() {
    navigate(NfcTestRoute)
}

fun NavController.navigateToCamera(watermarkData: WatermarkData) {
    navigate(CameraRoute(watermarkData))
}

fun NavController.navigateToFaceVerificationWithAutoSign() {
    navigate(TxFaceRoute)
}

/**
 * 导航到手动人脸捕获页面
 */
fun NavController.navigateToManualFaceCapture() {
    navigate(ManualFaceCaptureRoute)
}

/**
 * 导航到WebView页面
 * @param url 要加载的网页URL
 * @param title 页面标题
 */
fun NavController.navigateToWebView(url: String, title: String) {
    navigate(WebViewRoute(url, title))
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
    val appVersionModel by viewModel.appVersionModel.collectAsStateWithLifecycle()

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

    appVersionModel?.let {
        val updateViewModel: AppUpdateViewModel = hiltViewModel()
        updateViewModel.setAppVersionModel(it)
        
        AppUpdateDialog(
            viewModel = updateViewModel,
            onDismiss = {
                viewModel.clearAppVersionModel()
            }
        )
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
        composable<ServiceRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceRoute>()
            val context = LocalContext.current
            val selectedProjectsManager = EntryPointAccessors.fromApplication(
                context,
                SelectedProjectsManagerEntryPoint::class.java
            ).selectedProjectsManager()
            ServiceHoursScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest,
                selectedProjectsManager = selectedProjectsManager
            )
        }
        composable<NursingExecutionRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NursingExecutionRoute>()
            NursingExecutionScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest
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
            typeMap = mapOf(
                typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType,
                typeOf<EndOderInfo?>() to EndOderInfoNavType
            )
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NfcSignInRoute>()

            NfcWorkflowScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest,
                signInMode = route.signInMode,
                endOderInfo = route.endOrderParams
            )
        }
        composable<SelectServiceRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SelectServiceRoute>()
            SelectServiceScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest
            )
        }
        composable<PhotoUploadRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<PhotoUploadRoute>()
            PhotoUploadScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest
            )
        }
        composable<ServiceCountdownRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCountdownRoute>()
            ServiceCountdownScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest,
                projectIdList = route.projectIdList
            )
        }
        composable<TxFaceRoute> { backStackEntry ->
            FaceVerificationWithAutoSignScreen(
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
                onVerificationSuccess = {},
            )
        }
        composable<LocationTrackingRoute> { backStackEntry ->
            LocationTrackingScreen()
        }
        composable<ServiceCompleteRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCompleteRoute>()
            val context = LocalContext.current
            val selectedProjectsManager = EntryPointAccessors.fromApplication(
                context,
                SelectedProjectsManagerEntryPoint::class.java
            ).selectedProjectsManager()
            ServiceCompleteScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest,
                selectedProjectsManager = selectedProjectsManager
            )
        }

        composable<FaceRecognitionGuideRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<FaceRecognitionGuideRoute>()
            FaceRecognitionGuideScreen(navController = navController, orderInfoRequest = route.orderInfoRequest)
        }
        
        composable<SelectDeviceRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SelectDeviceRoute>()
            SelectDeviceScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest
            )
        }
        
        composable<IdentificationRoute>(
            typeMap = mapOf(typeOf<OrderInfoRequestModel>() to OrderInfoRequestModelNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<IdentificationRoute>()
            IdentificationScreen(
                navController = navController,
                orderInfoRequest = route.orderInfoRequest
            )
        }
        
        composable<UserListRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UserListRoute>()
            val userListType = when (route.listType) {
                UserListType.HAVE_SERVICE.name -> UserListType.HAVE_SERVICE
                UserListType.NO_SERVICE.name -> UserListType.NO_SERVICE
                else -> UserListType.HAVE_SERVICE // 默认值
            }
            UserListScreen(
                navController = navController,
                userListType = userListType
            )
        }
        
        composable<UserServiceRecordRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UserServiceRecordRoute>()
            UserServiceRecordScreen(
                userId = route.userId,
                userName = route.userName,
                userAddress = route.userAddress,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable<NfcTestRoute> {
            NfcTestScreen(navController = navController)
        }
        
        composable<CameraRoute>(
            typeMap = mapOf(typeOf<WatermarkData>() to WatermarkDataNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<CameraRoute>()
            CameraScreen(
                navController = navController,
                watermarkData = route.watermarkData
            )
        }
        
        composable<ManualFaceCaptureRoute> {
            ManualFaceCaptureScreen(
                onNavigateBack = { navController.popBackStack() },
                onFaceCaptured = { imagePath ->
                    // 返回到上一页面，并传递结果
                    navController.previousBackStackEntry?.savedStateHandle?.set(NavigationConstants.FACE_IMAGE_PATH_KEY, imagePath)
                    navController.popBackStack()
                }
            )
        }
        
        composable<WebViewRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WebViewRoute>()
            com.ytone.longcare.features.webview.ui.WebViewScreen(
                navController = navController,
                url = route.url,
                title = route.title
            )
        }
    }
}
