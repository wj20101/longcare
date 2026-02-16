package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.common.utils.safeNavigate
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.feature.home.FeatureEntry as HomeFeatureEntry
import com.ytone.longcare.feature.identification.FeatureEntry as IdentificationFeatureEntry
import com.ytone.longcare.feature.login.FeatureEntry as LoginFeatureEntry
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.features.update.ui.AppUpdateDialog
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel
import com.ytone.longcare.features.userlist.ui.UserListType

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
        registerAppNavGraphs(navController)
    }
}
