package com.ytone.longcare.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.facerecognition.api.FaceRecognitionGuideActions
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLauncher
import com.ytone.longcare.features.identification.api.IdentificationFeatureScreen
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationScreen
import com.ytone.longcare.features.identification.facecheck.FaceImageMetrics
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.selectdevice.api.SelectDeviceActions
import com.ytone.longcare.features.selectdevice.ui.SelectDeviceScreen
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.features.userlist.api.UserListActions
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.features.userservicerecord.ui.UserServiceRecordScreen
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.platform.face.rememberFaceSdkUiController
import kotlin.reflect.typeOf

internal val LocalFaceImageMetricsReporter =
    staticCompositionLocalOf<(FaceImageMetrics) -> Unit> { { _ -> } }

internal fun NavGraphBuilder.registerTxFaceRoute(navController: NavController) {
    composable<TxFaceRoute> {
        FaceVerificationWithAutoSignScreen(
            onNavigateBack = { navController.popBackStack() },
            onVerificationSuccess = {},
        )
    }
}

internal fun NavGraphBuilder.registerUserListRoute(navController: NavController) {
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
}

internal fun NavGraphBuilder.registerUserServiceRecordRoute(navController: NavController) {
    composable<UserServiceRecordRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserServiceRecordRoute>()
        UserServiceRecordScreen(
            userId = route.userId,
            userName = route.userName,
            userAddress = route.userAddress,
            onBackClick = { navController.popBackStack() }
        )
    }
}

internal fun NavGraphBuilder.registerFaceRecognitionGuideRoute(navController: NavController) {
    composable<FaceRecognitionGuideRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<FaceRecognitionGuideRoute>()
        FaceRecognitionGuideScreen(
            actions = FaceRecognitionGuideActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSelectService = { orderKey ->
                    navController.navigateToSelectService(orderKey)
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerSelectDeviceRoute(navController: NavController) {
    composable<SelectDeviceRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SelectDeviceRoute>()
        SelectDeviceScreen(
            actions = SelectDeviceActions(
                onNavigateBack = { navController.popBackStack() },
                onStartOrderNfcSignIn = { orderKey ->
                    navController.navigateToNfcSignInForStartOrder(orderKey)
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerIdentificationRoute(navController: NavController) {
    composable<IdentificationRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<IdentificationRoute>()
        val context = LocalContext.current
        val faceSdkUiController = rememberFaceSdkUiController()
        IdentificationFeatureScreen(
            actions = createIdentificationRouteActions(
                savedStateHandle = backStackEntry.savedStateHandle,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { watermarkData ->
                    navController.navigateToCamera(watermarkData)
                },
                onNavigateToManualFaceCapture = { navController.navigateToManualFaceCapture() },
                onNavigateToDefaultFaceVerification = { orderKey ->
                    navController.navigateToDefaultFaceVerification(orderKey)
                },
                onNavigateToSelectService = { orderKey ->
                    navController.navigateToSelectService(orderKey)
                },
            ),
            orderKey = route.orderParams.toOrderKey(),
            faceSdkLauncher = IdentificationFaceSdkLauncher { request, onEvent ->
                faceSdkUiController.start(
                    context = context,
                    config = request.config,
                    request = request.request,
                    onEvent = onEvent,
                )
            },
        )
    }
}

internal fun NavGraphBuilder.registerDefaultFaceVerificationRoute(navController: NavController) {
    composable<DefaultFaceVerificationRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType),
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<DefaultFaceVerificationRoute>()
        val reportPhotoMetrics = LocalFaceImageMetricsReporter.current
        DefaultFaceVerificationScreen(
            orderKey = route.orderParams.toOrderKey(),
            onNavigateBack = {
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
                    false,
                )
                navController.popBackStack()
            },
            onVerificationSuccess = {
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
                    true,
                )
                navController.popBackStack()
            },
            onPhotoPrepared = reportPhotoMetrics,
        )
    }
}

internal fun NavGraphBuilder.registerCameraRoute(navController: NavController) {
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
}

internal fun NavGraphBuilder.registerManualFaceCaptureRoute(navController: NavController) {
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
}

internal fun NavGraphBuilder.registerWebViewRoute(navController: NavController) {
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
}
