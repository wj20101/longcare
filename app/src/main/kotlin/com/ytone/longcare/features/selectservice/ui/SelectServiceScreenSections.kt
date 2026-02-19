package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.shared.vm.OrderDetailUiState

@Composable
internal fun SelectServiceScaffoldContent(
    paddingValues: PaddingValues,
    uiState: OrderDetailUiState,
    serviceItems: SnapshotStateList<ServiceItem>,
    selectServiceType: Int,
    isStarOrderLoading: Boolean,
    onToggleItemSelection: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onNextStep: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            TotalDurationDisplay(
                totalDuration = serviceItems.filter { it.isSelected }.sumOf { it.duration }
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val currentState = uiState) {
                is OrderDetailUiState.Loading -> {
                    CenterStateText(showProgress = true)
                }
                is OrderDetailUiState.Error -> {
                    CenterStateText(text = "加载失败: ${currentState.message}")
                }
                is OrderDetailUiState.Success -> {
                    ServiceSelectionList(
                        serviceItems = serviceItems,
                        onItemClick = onToggleItemSelection
                    )
                }
                is OrderDetailUiState.Initial -> {
                    CenterStateText(text = "正在初始化...")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        SelectServiceBottomActions(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectServiceType = selectServiceType,
            serviceItems = serviceItems,
            isStarOrderLoading = isStarOrderLoading,
            onToggleSelectAll = onToggleSelectAll,
            onNextStep = onNextStep
        )
    }
}
