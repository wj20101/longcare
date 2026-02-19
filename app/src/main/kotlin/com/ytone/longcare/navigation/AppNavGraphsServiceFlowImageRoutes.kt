package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownScreen
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.typeOf

internal fun NavGraphBuilder.registerPhotoUploadRoute(navController: NavController) {
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
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun NavGraphBuilder.registerServiceCountdownRoute(navController: NavController) {
    composable<ServiceCountdownRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<ServiceCountdownRoute>()
        ServiceCountdownScreen(
            actions = ServiceCountdownActions(
                onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() },
                onNavigateToEndServiceSelection = { orderKey, endType, projectIdList ->
                    navController.navigateToEndServiceSelection(orderKey, endType, projectIdList)
                },
                onNavigateToPhotoUpload = { orderKey, existingImages ->
                    backStackEntry.savedStateHandle.set(
                        NavigationConstants.EXISTING_IMAGES_KEY,
                        existingImages
                    )
                    navController.navigateToPhotoUpload(orderKey)
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
            orderKey = route.orderParams.toOrderKey(),
            projectIdList = route.projectIdList
        )
    }
}
