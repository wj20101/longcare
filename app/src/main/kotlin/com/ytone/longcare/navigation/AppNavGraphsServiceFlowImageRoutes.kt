package com.ytone.longcare.navigation

import androidx.compose.runtime.remember
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownScreen
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import kotlinx.coroutines.flow.MutableStateFlow

internal fun AppEntryProviderBuilder.registerPhotoUploadRoute(navController: AppNavigator) {
    destination<PhotoUploadRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<PhotoUploadRoute>()
        val existingImagesFlow = remember(backStackEntry.id) { navController.previousBackStackEntry
            ?.results
            ?.getStateFlow<Map<ImageTaskType, List<ImageTask>>?>(
                NavigationConstants.EXISTING_IMAGES_KEY, null
            ) ?: MutableStateFlow(null) }
        PhotoUploadScreen(
            actions = PhotoUploadActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { watermarkData ->
                    navController.navigateToCamera(watermarkData)
                },
                onPublishPhotoUploadResultAndNavigateBack = { imageTasksMap ->
                    navController.returnResult(NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, imageTasksMap)
                },
                existingImagesFlow = existingImagesFlow,
                clearExistingImages = {
                    navController.previousBackStackEntry?.results?.remove<Map<ImageTaskType, List<ImageTask>>>(
                        NavigationConstants.EXISTING_IMAGES_KEY
                    )
                },
                capturedImageUriFlow = backStackEntry.results.getStateFlow(
                    NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                    null
                ),
                clearCapturedImageUri = {
                    backStackEntry.results.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)
                }
            ),
            orderKey = route.orderParams.toOrderKey()
        )
    }
}

internal fun AppEntryProviderBuilder.registerServiceCountdownRoute(navController: AppNavigator) {
    destination<ServiceCountdownRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val route = backStackEntry.route<ServiceCountdownRoute>()
        ServiceCountdownScreen(
            actions = ServiceCountdownActions(
                onNavigateHomeAndClearStack = { navController.navigateToHomeAndClearStack() },
                onNavigateToEndServiceSelection = { orderKey, endType, projectIdList ->
                    navController.navigateToEndServiceSelection(orderKey, endType, projectIdList)
                },
                onNavigateToPhotoUpload = { orderKey, existingImages ->
                    backStackEntry.results.set(
                        NavigationConstants.EXISTING_IMAGES_KEY,
                        existingImages
                    )
                    navController.navigateToPhotoUpload(orderKey)
                },
                photoUploadResultFlow = backStackEntry.results.getStateFlow(
                    NavigationConstants.PHOTO_UPLOAD_RESULT_KEY,
                    null
                ),
                clearPhotoUploadResult = {
                    backStackEntry.results.remove<Map<ImageTaskType, List<ImageTask>>>(
                        NavigationConstants.PHOTO_UPLOAD_RESULT_KEY
                    )
                }
            ),
            orderKey = route.orderParams.toOrderKey(),
            projectIdList = route.projectIdList
        )
    }
}
