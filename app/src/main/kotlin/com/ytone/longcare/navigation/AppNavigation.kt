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

// ========== 导航扩展函数 ==========

/**
 * 从登录页面导航到主页，并清除登录页面的返回栈
 */
fun NavController.navigateToHomeFromLogin() {
    navigate(HomeRoute) {
        popUpTo(LoginRoute) { inclusive = true }
    }
}

/**
 * 导航到服务详情页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToService(orderParams: OrderNavParams) {
    navigate(ServiceRoute(orderParams))
}

/**
 * 导航到护理执行页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToNursingExecution(orderParams: OrderNavParams) {
    navigate(NursingExecutionRoute(orderParams))
}

/**
 * 导航到NFC签到页面（开始订单模式）
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToNfcSignInForStartOrder(orderParams: OrderNavParams) {
    navigate(NfcSignInRoute(orderParams = orderParams, signInMode = SignInMode.START_ORDER))
}

/**
 * 导航到NFC签到页面（结束订单模式）
 * @param orderParams 订单导航参数
 * @param params 结束订单的信息参数
 */
fun NavController.navigateToNfcSignInForEndOrder(orderParams: OrderNavParams, params: EndOderInfo) {
    navigate(
        NfcSignInRoute(
            orderParams = orderParams,
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
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToSelectService(orderParams: OrderNavParams) {
    navigate(SelectServiceRoute(orderParams))
}

/**
 * 导航到照片上传页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToPhotoUpload(orderParams: OrderNavParams) {
    navigate(PhotoUploadRoute(orderParams))
}

/**
 * 导航到服务倒计时页面
 * @param orderParams 订单导航参数
 * @param projectIdList 选中的项目ID列表
 */
fun NavController.navigateToServiceCountdown(orderParams: OrderNavParams, projectIdList: List<Int> = emptyList()) {
    navigate(ServiceCountdownRoute(orderParams = orderParams, projectIdList = projectIdList))
}

/**
 * 导航到结束服务选择页面
 * @param orderParams 订单导航参数
 * @param endType 结束类型
 * @param projectIdList 项目ID列表
 */
fun NavController.navigateToEndServiceSelection(orderParams: OrderNavParams, endType: Int, projectIdList: List<Int> = emptyList()) {
    navigate(EndServiceSelectionRoute(orderParams = orderParams, endType = endType, initialProjectIdList = projectIdList))
}

/**
 * 导航到服务完成页面
 * @param orderParams 订单导航参数
 * @param serviceCompleteData 服务完成数据
 */
fun NavController.navigateToServiceComplete(
    orderParams: OrderNavParams,
    serviceCompleteData: ServiceCompleteData
) {
    navigate(ServiceCompleteRoute(orderParams = orderParams, serviceCompleteData = serviceCompleteData)) {
        // 服务完成时，清除之前所有的服务流程页面（保留Home），确保 SharedOrderDetailViewModel 被销毁 -> 停止定位
        popUpTo(HomeRoute) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * 导航到人脸识别引导页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToFaceRecognitionGuide(orderParams: OrderNavParams) {
    navigate(FaceRecognitionGuideRoute(orderParams = orderParams))
}

/**
 * 导航到选择设备页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToSelectDevice(orderParams: OrderNavParams) {
    navigateToNfcSignInForStartOrder(orderParams)
}

/**
 * 导航到身份认证页面
 * @param orderParams 订单导航参数
 */
fun NavController.navigateToIdentification(orderParams: OrderNavParams) {
    val currentRoute = this.currentBackStackEntry?.destination?.route ?: return
    navigate(IdentificationRoute(orderParams)) {
        popUpTo(currentRoute) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * 导航到用户列表页面
 */
fun NavController.navigateToUserList(listType: String) {
    navigate(UserListRoute(listType))
}

fun NavController.navigateToHaveServiceUserList() {
    navigateToUserList(UserListType.HAVE_SERVICE.name)
}

fun NavController.navigateToNoServiceUserList() {
    navigateToUserList(UserListType.NO_SERVICE.name)
}

fun NavController.navigateToHomeAndClearStack() {
    navigate(HomeRoute) {
        popUpTo(0) { inclusive = false }
        launchSingleTop = true
    }
}

fun NavController.navigateToUserServiceRecord(userId: Long, userName: String, userAddress: String) {
    navigate(UserServiceRecordRoute(userId, userName, userAddress))
}

fun NavController.navigateToNfcTest() {
    navigate(NfcTestRoute)
}

fun NavController.navigateToCamera(watermarkData: WatermarkData) {
    navigate(CameraRoute(watermarkData))
}

fun NavController.navigateToFaceVerificationWithAutoSign() {
    navigate(TxFaceRoute)
}

fun NavController.navigateToManualFaceCapture() {
    navigate(ManualFaceCaptureRoute)
}

fun NavController.navigateToWebView(url: String, title: String) {
    navigate(WebViewRoute(url, title))
}

// ========== 主要Composable ==========

@Composable
fun MainApp(viewModel: MainViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val appVersionModel by viewModel.appVersionModel.collectAsStateWithLifecycle()

    when (sessionState) {
        is SessionState.Unknown -> SplashScreen()
        is SessionState.LoggedIn -> AppNavigation(startDestination = HomeRoute)
        is SessionState.LoggedOut -> AppNavigation(startDestination = LoginRoute)
    }

    appVersionModel?.let {
        val updateViewModel: AppUpdateViewModel = hiltViewModel()
        updateViewModel.setAppVersionModel(it)
        AppUpdateDialog(
            viewModel = updateViewModel,
            onDismiss = { viewModel.clearAppVersionModel() }
        )
    }
}

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
        composable<ServiceRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceRoute>()
            ServiceHoursScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<NursingExecutionRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NursingExecutionRoute>()
            NursingExecutionScreen(
                navController = navController,
                orderParams = route.orderParams
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
                typeOf<EndOderInfo?>() to EndOderInfoNavType,
                typeOf<OrderNavParams>() to OrderNavParamsNavType
            )
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NfcSignInRoute>()
            NfcWorkflowScreen(
                navController = navController,
                orderParams = route.orderParams,
                signInMode = route.signInMode,
                endOderInfo = route.endOrderParams
            )
        }
        composable<SelectServiceRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SelectServiceRoute>()
            SelectServiceScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<PhotoUploadRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<PhotoUploadRoute>()
            PhotoUploadScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<ServiceCountdownRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCountdownRoute>()
            ServiceCountdownScreen(
                navController = navController,
                orderParams = route.orderParams,
                projectIdList = route.projectIdList
            )
        }
        composable<TxFaceRoute> {
            FaceVerificationWithAutoSignScreen(
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
                onVerificationSuccess = {},
            )
        }
        composable<LocationTrackingRoute> {
            LocationTrackingScreen()
        }
        composable<ServiceCompleteRoute>(
            typeMap = mapOf(
                typeOf<ServiceCompleteData>() to ServiceCompleteDataNavType,
                typeOf<OrderNavParams>() to OrderNavParamsNavType
            )
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCompleteRoute>()
            ServiceCompleteScreen(
                navController = navController,
                orderParams = route.orderParams,
                serviceCompleteData = route.serviceCompleteData
            )
        }
        composable<FaceRecognitionGuideRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<FaceRecognitionGuideRoute>()
            FaceRecognitionGuideScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<SelectDeviceRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SelectDeviceRoute>()
            SelectDeviceScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<IdentificationRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<IdentificationRoute>()
            IdentificationScreen(
                navController = navController,
                orderParams = route.orderParams
            )
        }
        composable<UserListRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UserListRoute>()
            val userListType = when (route.listType) {
                UserListType.HAVE_SERVICE.name -> UserListType.HAVE_SERVICE
                UserListType.NO_SERVICE.name -> UserListType.NO_SERVICE
                else -> UserListType.HAVE_SERVICE
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
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        NavigationConstants.FACE_IMAGE_PATH_KEY, imagePath
                    )
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
        composable<EndServiceSelectionRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<EndServiceSelectionRoute>()
            com.ytone.longcare.features.endservice.ui.EndServiceSelectionScreen(
                navController = navController,
                orderParams = route.orderParams,
                endType = route.endType,
                initialProjectIdList = route.initialProjectIdList
            )
        }
    }
}
