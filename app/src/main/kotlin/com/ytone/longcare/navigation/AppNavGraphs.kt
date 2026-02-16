package com.ytone.longcare.navigation

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.endservice.api.EndServiceSelectionActions
import com.ytone.longcare.features.endservice.ui.EndServiceSelectionScreen
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.facerecognition.api.FaceRecognitionGuideActions
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.location.ui.LocationTrackingScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.ui.NfcWorkflowScreen
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.nfctest.ui.NfcTestScreen
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.features.selectdevice.api.SelectDeviceActions
import com.ytone.longcare.features.selectdevice.ui.SelectDeviceScreen
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.ui.SelectServiceScreen
import com.ytone.longcare.features.servicecomplete.api.ServiceCompleteActions
import com.ytone.longcare.features.servicecomplete.ui.ServiceCompleteScreen
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownScreen
import com.ytone.longcare.features.servicehours.api.ServiceHoursActions
import com.ytone.longcare.features.servicehours.ui.ServiceHoursScreen
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderType
import com.ytone.longcare.features.serviceorders.ui.ServiceOrdersListScreen
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.features.userlist.api.UserListActions
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.features.userservicerecord.ui.UserServiceRecordScreen
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.typeOf

internal fun NavGraphBuilder.registerAppNavGraphs(navController: NavController) {
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
