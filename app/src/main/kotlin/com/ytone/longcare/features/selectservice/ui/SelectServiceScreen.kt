package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.vm.SelectServiceViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.StarOrderUiState
import com.ytone.longcare.theme.bgGradientBrush
import kotlinx.coroutines.launch

data class ServiceItem(
    val id: Int,
    val name: String,
    val duration: Int,
    var isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectServiceScreen(
    actions: SelectServiceActions,
    orderKey: OrderKey,
    selectServiceViewModel: SelectServiceViewModel = hiltViewModel(),
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val starOrderState by sharedViewModel.starOrderState.collectAsStateWithLifecycle()

    CustomBackHandler(customAction = actions.onNavigateBack)

    LaunchedEffect(orderKey) {
        loadOrderInfoIfNeeded(sharedViewModel, orderKey)
    }

    LaunchedEffect(starOrderState) {
        if (starOrderState is StarOrderUiState.Success) {
            sharedViewModel.resetStarOrderState()
        }
    }

    var selectServiceType by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        selectServiceType = selectServiceViewModel.getSelectServiceType()
    }

    val serviceItems = remember { mutableStateListOf<ServiceItem>() }
    LaunchedEffect(uiState, selectServiceType) {
        updateServiceItemsFromUiState(uiState, selectServiceType, serviceItems)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                SelectServiceTopBar(onNavigateBack = actions.onNavigateBack)
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            SelectServiceScaffoldContent(
                paddingValues = paddingValues,
                uiState = uiState,
                serviceItems = serviceItems,
                selectServiceType = selectServiceType,
                isStarOrderLoading = starOrderState is StarOrderUiState.Loading,
                onToggleItemSelection = { clickedIndex ->
                    val currentItem = serviceItems[clickedIndex]
                    serviceItems[clickedIndex] = currentItem.copy(
                        isSelected = !currentItem.isSelected
                    )
                },
                onToggleSelectAll = {
                    val isAllSelected = serviceItems.all { it.isSelected }
                    for (i in serviceItems.indices) {
                        serviceItems[i] = serviceItems[i].copy(isSelected = !isAllSelected)
                    }
                },
                onNextStep = singleClick {
                    val selectedIds = selectedProjectIds(serviceItems)
                    sharedViewModel.starOrder(orderKey.orderId, selectedIds.map(Int::toLong)) {
                        coroutineScope.launch {
                            selectServiceViewModel.updateSelectedProjects(
                                orderKey = orderKey,
                                selectedProjectIds = selectedIds
                            )
                            actions.onNavigateToServiceCountdown(orderKey, selectedIds)
                        }
                    }
                }
            )
        }
    }
}
