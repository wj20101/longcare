package com.ytone.longcare.features.nursingexecution.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
fun NursingExecutionScreen(
    actions: NursingExecutionActions,
    orderKey: OrderKey,
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
) {
    val uiState = sharedViewModel.uiState.collectAsStateWithLifecycle().value

    CustomBackHandler(customAction = actions.onNavigateBack)

    LaunchedEffect(orderKey) {
        sharedViewModel.getOrderInfo(orderKey, forceRefresh = true)
    }

    when (val state = uiState) {
        is OrderDetailUiState.Loading,
        is OrderDetailUiState.Initial -> {
            LoadingScreen()
        }

        is OrderDetailUiState.Success -> {
            NursingExecutionContent(
                actions = actions,
                orderInfo = state.orderInfo,
                orderKey = orderKey,
                onNavigateToCountdown = { projectList ->
                    val selectedProjectIds = sharedViewModel.getSelectedProjectIdsOrDefault(
                        orderKey = orderKey,
                        projectList = projectList
                    )
                    actions.onNavigateToServiceCountdown(orderKey, selectedProjectIds)
                }
            )
        }

        is OrderDetailUiState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { sharedViewModel.getOrderInfo(orderKey, forceRefresh = true) }
            )
        }
    }
}
