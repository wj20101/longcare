package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
internal fun ServiceCountdownLifecycleEffects(
    actions: ServiceCountdownActions,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel,
    countdownViewModel: ServiceCountdownViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    countdownState: ServiceCountdownState,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    exactAlarmPermissionLauncher: ActivityResultLauncher<android.content.Intent>,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onPermissionDialogRequired: (String) -> Unit,
    onOrderStateError: (String) -> Unit
) {
    val orderStateError by countdownViewModel.orderStateError.collectAsStateWithLifecycle()
    val latestCountdownState by rememberUpdatedState(countdownState)

    LaunchedEffect(orderStateError) {
        orderStateError?.let { stateModel ->
            onOrderStateError(buildOrderStateErrorMessage(stateModel))
        }
    }

    LaunchedEffect(orderKey) {
        sharedViewModel.getCachedOrderInfo(orderKey)
        sharedViewModel.getOrderInfo(orderKey)

        checkLocationPermissionAndStart(
            context = context,
            permissionLauncher = permissionLauncher,
            onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
        )

        countdownViewModel.loadUploadedImagesFromRepository(orderKey)
        countdownViewModel.startOrderStatePolling(orderKey)

        actions.photoUploadResultFlow.collect { result ->
            result?.let {
                countdownViewModel.handlePhotoUploadResult(it)
                actions.clearPhotoUploadResult()
            }
        }
    }

    LaunchedEffect(orderKey, projectIdList) {
        setupCountdownSessionIfNeeded(
            context = context,
            orderKey = orderKey,
            projectIdList = projectIdList,
            sharedViewModel = sharedViewModel,
            countdownViewModel = countdownViewModel,
            notificationPermissionLauncher = notificationPermissionLauncher,
            exactAlarmPermissionLauncher = exactAlarmPermissionLauncher,
            onPermissionDialogRequired = onPermissionDialogRequired
        )
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (countdownViewModel.isInitialized() && countdownState != ServiceCountdownState.ENDED) {
                val orderInfo = sharedViewModel.getCachedOrderInfo(orderKey)
                orderInfo?.let {
                    countdownViewModel.refreshCountdownDisplay(
                        orderKey = orderKey,
                        projectList = it.projectList ?: emptyList(),
                        selectedProjectIds = projectIdList
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            countdownViewModel.stopOrderStatePolling()

            val disposeActions = resolveServiceCountdownDisposeActions()
            if (disposeActions.cancelCountdownAlarm && latestCountdownState != ServiceCountdownState.ENDED) {
                countdownViewModel.cancelCountdownAlarm()
            }
            if (disposeActions.stopAlarmRingtone && latestCountdownState != ServiceCountdownState.ENDED) {
                AlarmRingtoneService.stopRingtone(context)
            }
        }
    }
}
