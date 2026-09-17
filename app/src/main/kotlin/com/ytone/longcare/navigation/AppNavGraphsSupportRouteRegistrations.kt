package com.ytone.longcare.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.features.sales.SalesViewModel

import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationScreen
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.ui.IdentificationScreen
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.userlist.api.UserListActions
import com.ytone.longcare.features.userlist.ui.UserListScreen
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.features.userservicerecord.ui.UserServiceRecordScreen
import com.ytone.longcare.features.webview.api.WebViewActions
import com.ytone.longcare.features.webview.ui.WebViewScreen

internal fun AppEntryProviderBuilder.registerUserListRoute(navController: AppNavigator) {
    destination<UserListRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<UserListRoute>()
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

internal fun AppEntryProviderBuilder.registerUserServiceRecordRoute(navController: AppNavigator) {
    destination<UserServiceRecordRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<UserServiceRecordRoute>()
        UserServiceRecordScreen(
            userId = route.userId,
            userName = route.userName,
            userAddress = route.userAddress,
            onBackClick = { navController.popBackStack() }
        )
    }
}

internal fun AppEntryProviderBuilder.registerIdentificationRoute(navController: AppNavigator) {
    destination<IdentificationRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<IdentificationRoute>()
        IdentificationScreen(
            actions = IdentificationActions(
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
                capturedImageUriFlow = backStackEntry.results.getStateFlow(
                    NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                    null
                ),
                clearCapturedImageUri = {
                    backStackEntry.results.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)
                },
                faceImagePathFlow = backStackEntry.results.getStateFlow(
                    NavigationConstants.FACE_IMAGE_PATH_KEY,
                    null
                ),
                clearFaceImagePath = {
                    backStackEntry.results.remove<String>(NavigationConstants.FACE_IMAGE_PATH_KEY)
                },
                defaultFaceVerificationResultFlow = backStackEntry.results.getStateFlow(
                    NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
                    null,
                ),
                clearDefaultFaceVerificationResult = {
                    backStackEntry.results.remove<Boolean>(
                        NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
                    )
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun AppEntryProviderBuilder.registerDefaultFaceVerificationRoute(navController: AppNavigator) {
    destination<DefaultFaceVerificationRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<DefaultFaceVerificationRoute>()
        DefaultFaceVerificationScreen(
            orderKey = route.orderParams.toOrderKey(),
            onNavigateBack = {
                navController.returnResult(NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY, false)
            },
            onVerificationSuccess = {
                navController.returnResult(NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY, true)
            },
        )
    }
}

internal fun AppEntryProviderBuilder.registerCameraRoute(navController: AppNavigator) {
    destination<CameraRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<CameraRoute>()
        CameraScreen(
            actions = CameraActions(
                onImageCaptured = { capturedImageUri ->
                    navController.returnResult(NavigationConstants.CAPTURED_IMAGE_URI_KEY, capturedImageUri)
                }
            ),
            watermarkData = route.watermarkData
        )
    }
}

internal fun AppEntryProviderBuilder.registerManualFaceCaptureRoute(navController: AppNavigator) {
    destination<ManualFaceCaptureRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        ManualFaceCaptureScreen(
            onNavigateBack = { navController.popBackStack() },
            onFaceCaptured = { imagePath ->
                navController.returnResult(NavigationConstants.FACE_IMAGE_PATH_KEY, imagePath)
            }
        )
    }
}

internal fun AppEntryProviderBuilder.registerWebViewRoute(navController: AppNavigator) {
    destination<WebViewRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<WebViewRoute>()
        val salesViewModel = if (route.isEvaluation) {
            hiltViewModel<SalesViewModel>(LocalHomeViewModelStoreOwner.current)
        } else null
        WebViewScreen(
            actions = WebViewActions(
                onNavigateBack = { navController.popBackStack() },
                isCurrentPage = navController::canHandleCallback,
                onCloseFromH5 = {
                    salesViewModel?.onEvaluationH5Closed()
                    navController.popBackStack()
                },
            ),
            url = route.url,
            title = route.title,
        )
    }
}
