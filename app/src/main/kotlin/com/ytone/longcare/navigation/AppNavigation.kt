package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ytone.longcare.common.utils.safeNavigate
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.location.ui.LocationTrackingScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownScreen
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.ui.NfcWorkflowScreen
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.features.endservice.api.EndServiceSelectionActions
import com.ytone.longcare.features.endservice.ui.EndServiceSelectionScreen
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicehours.api.ServiceHoursActions
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.features.servicecomplete.api.ServiceCompleteActions
import com.ytone.longcare.features.servicecomplete.ui.ServiceCompleteScreen
import com.ytone.longcare.features.facerecognition.api.FaceRecognitionGuideActions
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.selectdevice.ui.SelectDeviceScreen
import com.ytone.longcare.features.userlist.api.UserListActions
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.userservicerecord.ui.UserServiceRecordScreen
import com.ytone.longcare.features.nfctest.ui.NfcTestScreen
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.selectdevice.api.SelectDeviceActions
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.feature.home.FeatureEntry as HomeFeatureEntry
import com.ytone.longcare.feature.identification.FeatureEntry as IdentificationFeatureEntry
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.feature.login.FeatureEntry as LoginFeatureEntry
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.typeOf

private val featureRouteRegistry = setOf(
    LoginFeatureEntry.ROUTE,
    HomeFeatureEntry.ROUTE,
    IdentificationFeatureEntry.ROUTE
)

private fun resolveStartDestination(sessionState: SessionState): Any = when (sessionState) {
    is SessionState.Unknown -> SplashRoute
    is SessionState.LoggedIn -> HomeRoute
    is SessionState.LoggedOut -> LoginRoute
}

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
    safeNavigate(EndServiceSelectionRoute(orderParams = orderParams, endType = endType, initialProjectIdList = projectIdList))
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
    safeNavigate(HomeRoute) {
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
    val startDestination = resolveStartDestination(sessionState)

    if (startDestination == SplashRoute) {
        SplashScreen()
    } else {
        AppNavigation(startDestination = startDestination)
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

private object SplashRoute

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
    check(featureRouteRegistry.size == 3) {
        "Feature route registry is incomplete."
    }
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
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
        composable<ServiceRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceRoute>()
            ServiceHoursScreen(
                actions = ServiceHoursActions(
                    onNavigateBack = { navController.popBackStack() }
                ),
                orderParams = route.orderParams
            )
        }
        composable<NursingExecutionRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NursingExecutionRoute>()
            NursingExecutionScreen(
                actions = NursingExecutionActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToServiceCountdown = { orderParams, projectIdList ->
                        navController.navigateToServiceCountdown(orderParams, projectIdList)
                    },
                    onNavigateToSelectDevice = { orderParams ->
                        navController.navigateToSelectDevice(orderParams)
                    }
                ),
                orderParams = route.orderParams
            )
        }
        composable<CarePlansListRoute> {
            val parentEntry = remember(navController.currentBackStackEntry) {
                navController.getBackStackEntry(HomeRoute)
            }
            val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
            ServiceOrdersListScreen(
                actions = ServiceOrdersListActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNursingExecution = { orderParams ->
                        navController.navigateToNursingExecution(orderParams)
                    },
                    onNavigateToService = { orderParams ->
                        navController.navigateToService(orderParams)
                    }
                ),
                orderType = ServiceOrderType.PENDING_CARE_PLANS,
                todayOrderViewModel = todayOrderViewModel
            )
        }
        composable<ServiceRecordsListRoute> {
            val parentEntry = remember(navController.currentBackStackEntry) {
                navController.getBackStackEntry(HomeRoute)
            }
            val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
            ServiceOrdersListScreen(
                actions = ServiceOrdersListActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNursingExecution = { orderParams ->
                        navController.navigateToNursingExecution(orderParams)
                    },
                    onNavigateToService = { orderParams ->
                        navController.navigateToService(orderParams)
                    }
                ),
                orderType = ServiceOrderType.SERVICE_RECORDS,
                todayOrderViewModel = todayOrderViewModel
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
                actions = NfcWorkflowActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() },
                    onNavigateToIdentification = { orderParams ->
                        navController.navigateToIdentification(orderParams)
                    },
                    onNavigateToServiceComplete = { orderParams, serviceCompleteData ->
                        navController.navigateToServiceComplete(
                            orderParams = orderParams,
                            serviceCompleteData = serviceCompleteData
                        )
                    }
                ),
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
                actions = SelectServiceActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToServiceCountdown = { orderParams, projectIdList ->
                        navController.navigateToServiceCountdown(
                            orderParams = orderParams,
                            projectIdList = projectIdList
                        )
                    }
                ),
                orderParams = route.orderParams
            )
        }
        composable<PhotoUploadRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<PhotoUploadRoute>()
            val existingImagesFlow = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow<Map<ImageTaskType, List<ImageTask>>?>(
                    NavigationConstants.EXISTING_IMAGES_KEY, null
                ) ?: MutableStateFlow(null)
            PhotoUploadScreen(
                actions = PhotoUploadActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { watermarkData ->
                        navController.navigateToCamera(watermarkData)
                    },
                    onPublishPhotoUploadResultAndNavigateBack = { imageTasksMap ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY,
                            imageTasksMap
                        )
                        navController.popBackStack()
                    },
                    existingImagesFlow = existingImagesFlow,
                    clearExistingImages = {
                        navController.previousBackStackEntry?.savedStateHandle?.remove<Map<ImageTaskType, List<ImageTask>>>(
                            NavigationConstants.EXISTING_IMAGES_KEY
                        )
                    },
                    capturedImageUriFlow = backStackEntry.savedStateHandle.getStateFlow(
                        NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                        null
                    ),
                    clearCapturedImageUri = {
                        backStackEntry.savedStateHandle.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)
                    }
                ),
                orderParams = route.orderParams
            )
        }
        composable<ServiceCountdownRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceCountdownRoute>()
            ServiceCountdownScreen(
                actions = ServiceCountdownActions(
                    onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() },
                    onNavigateToEndServiceSelection = { orderParams, endType, projectIdList ->
                        navController.navigateToEndServiceSelection(orderParams, endType, projectIdList)
                    },
                    onNavigateToPhotoUpload = { orderParams, existingImages ->
                        backStackEntry.savedStateHandle.set(
                            NavigationConstants.EXISTING_IMAGES_KEY,
                            existingImages
                        )
                        navController.navigateToPhotoUpload(orderParams)
                    },
                    photoUploadResultFlow = backStackEntry.savedStateHandle.getStateFlow(
                        NavigationConstants.PHOTO_UPLOAD_RESULT_KEY,
                        null
                    ),
                    clearPhotoUploadResult = {
                        backStackEntry.savedStateHandle.remove<Map<ImageTaskType, List<ImageTask>>>(
                            NavigationConstants.PHOTO_UPLOAD_RESULT_KEY
                        )
                    }
                ),
                orderParams = route.orderParams,
                projectIdList = route.projectIdList
            )
        }
        composable<TxFaceRoute> {
            FaceVerificationWithAutoSignScreen(
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
                actions = ServiceCompleteActions(
                    onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() }
                ),
                orderParams = route.orderParams,
                serviceCompleteData = route.serviceCompleteData
            )
        }
        composable<FaceRecognitionGuideRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<FaceRecognitionGuideRoute>()
            FaceRecognitionGuideScreen(
                actions = FaceRecognitionGuideActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSelectService = { orderParams ->
                        navController.navigateToSelectService(orderParams)
                    }
                ),
                orderParams = route.orderParams
            )
        }
        composable<SelectDeviceRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<SelectDeviceRoute>()
            SelectDeviceScreen(
                actions = SelectDeviceActions(
                    onNavigateBack = { navController.popBackStack() },
                    onStartOrderNfcSignIn = { orderParams ->
                        navController.navigateToNfcSignInForStartOrder(orderParams)
                    }
                ),
                orderParams = route.orderParams
            )
        }
        composable<IdentificationRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<IdentificationRoute>()
            IdentificationScreen(
                actions = IdentificationActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCamera = { watermarkData ->
                        navController.navigateToCamera(watermarkData)
                    },
                    onNavigateToManualFaceCapture = { navController.navigateToManualFaceCapture() },
                    onNavigateToSelectService = { orderParams ->
                        navController.navigateToSelectService(orderParams)
                    },
                    capturedImageUriFlow = backStackEntry.savedStateHandle.getStateFlow(
                        NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                        null
                    ),
                    clearCapturedImageUri = {
                        backStackEntry.savedStateHandle.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)
                    },
                    faceImagePathFlow = backStackEntry.savedStateHandle.getStateFlow(
                        NavigationConstants.FACE_IMAGE_PATH_KEY,
                        null
                    ),
                    clearFaceImagePath = {
                        backStackEntry.savedStateHandle.remove<String>(NavigationConstants.FACE_IMAGE_PATH_KEY)
                    }
                ),
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
                actions = UserListActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToUserServiceRecord = { userId, userName, userAddress ->
                        navController.navigateToUserServiceRecord(userId, userName, userAddress)
                    }
                ),
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
            NfcTestScreen(
                actions = NfcTestActions(
                    onNavigateBack = { navController.popBackStack() }
                )
            )
        }
        composable<CameraRoute>(
            typeMap = mapOf(typeOf<WatermarkData>() to WatermarkDataNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<CameraRoute>()
            CameraScreen(
                actions = CameraActions(
                    onImageCaptured = { capturedImageUri ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                            capturedImageUri
                        )
                        navController.popBackStack()
                    }
                ),
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
            WebViewScreen(
                actions = WebViewActions(
                    onNavigateBack = { navController.navigateUp() }
                ),
                url = route.url,
                title = route.title
            )
        }
        composable<EndServiceSelectionRoute>(
            typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<EndServiceSelectionRoute>()
            EndServiceSelectionScreen(
                actions = EndServiceSelectionActions(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNfcSignInForEndOrder = { orderParams, params ->
                        navController.navigateToNfcSignInForEndOrder(orderParams, params)
                    }
                ),
                orderParams = route.orderParams,
                endType = route.endType,
                initialProjectIdList = route.initialProjectIdList
            )
        }
    }
}
