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
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.ui.NfcWorkflowScreen
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.features.nursingexecution.ui.NursingExecutionScreen
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.ui.PhotoUploadScreen
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
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.typeOf

internal fun NavGraphBuilder.registerServiceFlowNavGraphs(navController: NavController) {
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
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(orderKey.toOrderNavParams(), projectIdList)
                },
                onNavigateToSelectDevice = { orderKey ->
                    navController.navigateToSelectDevice(orderKey.toOrderNavParams())
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
                onNavigateToNursingExecution = { orderKey ->
                    navController.navigateToNursingExecution(orderKey.toOrderNavParams())
                },
                onNavigateToService = { orderKey ->
                    navController.navigateToService(orderKey.toOrderNavParams())
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
                onNavigateToNursingExecution = { orderKey ->
                    navController.navigateToNursingExecution(orderKey.toOrderNavParams())
                },
                onNavigateToService = { orderKey ->
                    navController.navigateToService(orderKey.toOrderNavParams())
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
                onNavigateToIdentification = { orderKey ->
                    navController.navigateToIdentification(orderKey.toOrderNavParams())
                },
                onNavigateToServiceComplete = { orderKey, serviceCompleteData ->
                    navController.navigateToServiceComplete(
                        orderParams = orderKey.toOrderNavParams(),
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
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(
                        orderParams = orderKey.toOrderNavParams(),
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
                onNavigateToEndServiceSelection = { orderKey, endType, projectIdList ->
                    navController.navigateToEndServiceSelection(orderKey.toOrderNavParams(), endType, projectIdList)
                },
                onNavigateToPhotoUpload = { orderKey, existingImages ->
                    backStackEntry.savedStateHandle.set(
                        NavigationConstants.EXISTING_IMAGES_KEY,
                        existingImages
                    )
                    navController.navigateToPhotoUpload(orderKey.toOrderNavParams())
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

    composable<EndServiceSelectionRoute>(
        typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType)
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<EndServiceSelectionRoute>()
        EndServiceSelectionScreen(
            actions = EndServiceSelectionActions(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNfcSignInForEndOrder = { orderKey, params ->
                    navController.navigateToNfcSignInForEndOrder(orderKey.toOrderNavParams(), params)
                }
            ),
            orderParams = route.orderParams,
            endType = route.endType,
            initialProjectIdList = route.initialProjectIdList
        )
    }
}
