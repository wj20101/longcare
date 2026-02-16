package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.facerecognition.api.FaceRecognitionGuideActions
import com.ytone.longcare.features.facerecognition.ui.FaceRecognitionGuideScreen
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.location.ui.LocationTrackingScreen
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.nfctest.ui.NfcTestScreen
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.model.WatermarkData
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
import kotlin.reflect.typeOf

internal fun NavGraphBuilder.registerSupportNavGraphs(navController: NavController) {
    composable<TxFaceRoute> {
        FaceVerificationWithAutoSignScreen(
            onNavigateBack = { navController.popBackStack() },
            onVerificationSuccess = {},
        )
    }

    composable<LocationTrackingRoute> {
        LocationTrackingScreen()
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
}
