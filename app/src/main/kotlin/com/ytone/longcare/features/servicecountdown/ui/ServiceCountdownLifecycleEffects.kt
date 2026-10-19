package com.ytone.longcare.features.servicecountdown.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

/** One initial explanation per saved order screen; requests require a separate user action. */
@Composable
internal fun ServiceCountdownInitialPermissionCheck(orderKey: OrderKey, checkPermissions: () -> Unit) {
    var checked by rememberSaveable(orderKey) { mutableStateOf(false) }
    LaunchedEffect(orderKey) {
        if (!checked) {
            checked = true
            checkPermissions()
        }
    }
}

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
    onOrderStateError: (String) -> Unit
) {
    val orderStateError by countdownViewModel.orderStateError.collectAsStateWithLifecycle()
    LaunchedEffect(orderStateError) {
        orderStateError?.let { stateModel ->
            onOrderStateError(buildOrderStateErrorMessage(context, stateModel))
        }
    }

    ServiceCountdownTimerLifecycleEffect(countdownViewModel, orderKey, projectIdList, lifecycleOwner)

    LaunchedEffect(orderKey) {
        sharedViewModel.getOrderInfo(orderKey)

        countdownViewModel.loadUploadedImagesFromRepository(orderKey)
        countdownViewModel.startOrderStatePolling(orderKey)

        actions.photoUploadResultFlow.collect { result ->
            result?.let {
                countdownViewModel.handlePhotoUploadResult(it)
                actions.clearPhotoUploadResult()
            }
        }
    }

    LaunchedEffect(lifecycleOwner, orderKey, projectIdList) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // A previous confirmation may have completed after Android disallowed an FGS start.
            // Retry only from this visible business screen, never from a background scheduler.
            if (countdownViewModel.countdownState.value != ServiceCountdownState.ENDED &&
                UnifiedPermissionHelper.hasLocationPermission(context) &&
                UnifiedPermissionHelper.isLocationServiceEnabled(context)
            ) {
                locationTrackingViewModel.startTracking(orderKey)
            }
        }
    }

    DisposableEffect(orderKey, countdownViewModel) {
        onDispose {
            countdownViewModel.stopOrderStatePolling()
        }
    }
}

/** 业务排程不随可见性取消，只有显示刷新跟随 RESUMED。 */
@Composable
internal fun ServiceCountdownTimerLifecycleEffect(
    countdownViewModel: ServiceCountdownViewModel,
    orderKey: OrderKey,
    projectIdList: List<Int>,
    lifecycleOwner: LifecycleOwner,
) {
    LaunchedEffect(countdownViewModel, orderKey, projectIdList) {
        countdownViewModel.synchronizeCountdownSession(orderKey, projectIdList)
    }
    var hasResumed by remember(lifecycleOwner, countdownViewModel, orderKey, projectIdList) { mutableStateOf(false) }
    LifecycleResumeEffect(countdownViewModel, orderKey to projectIdList, lifecycleOwner = lifecycleOwner) {
        countdownViewModel.startDisplayUpdates()
        // 首次已由进入页的任务准备；设置返回才重排，避免缓存命中时重复读取。
        if (hasResumed) countdownViewModel.synchronizeCountdownSession(orderKey, projectIdList)
        hasResumed = true
        onPauseOrDispose {
            countdownViewModel.stopDisplayUpdates()
        }
    }
}
