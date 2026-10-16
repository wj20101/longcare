package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.PermissionGuideId
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.core.ui.permission.BackgroundSettingsDialog
import com.ytone.longcare.core.ui.R as CoreUiR
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.vm.SelectServiceViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.StarOrderUiState
import com.ytone.longcare.theme.bgGradientBrush

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
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentActions by rememberUpdatedState(actions)
    val uiState by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val starOrderState by sharedViewModel.starOrderState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CustomBackHandler(customAction = actions.onNavigateBack)

    LaunchedEffect(orderKey) {
        sharedViewModel.getOrderInfo(orderKey)
    }

    LaunchedEffect(sharedViewModel, lifecycleOwner, orderKey) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sharedViewModel.starOrderState.collect { result ->
                when (result) {
                    is StarOrderUiState.Success -> if (result.orderKey == orderKey) {
                        currentActions.onNavigateToServiceCountdown(result.orderKey, result.selectedProjectIds)
                        sharedViewModel.acknowledgeStartResult(result)
                    }
                    is StarOrderUiState.Error -> {
                        snackbarHostState.showSnackbar(result.message)
                        sharedViewModel.acknowledgeStartResult(result)
                    }
                    else -> Unit
                }
            }
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

    // Save only the pending selection and guide identities; Settings intents and system state are rebuilt.
    var pendingStartProjectIds by rememberSaveable(orderKey) { mutableStateOf<IntArray?>(null) }
    var pendingGuideIds by rememberSaveable(orderKey) { mutableStateOf(emptyArray<String>()) }

    fun startService(selectedIds: List<Int>) {
        sharedViewModel.starOrder(orderKey, selectedIds)
    }

    fun dismissPendingStart() {
        PermissionGuideId.entries.filter { it.name in pendingGuideIds }.forEach { id ->
            DeviceCompatibilityHelper.markGuideShown(context, id)
        }
        pendingStartProjectIds = null
        pendingGuideIds = emptyArray()
    }

    fun confirmPendingStart() {
        val selectedIds = pendingStartProjectIds?.toList()
        dismissPendingStart()
        selectedIds?.let(::startService)
    }

    fun showCompatibilityGuideIfNeeded(selectedIds: List<Int>): Boolean {
        val items = DeviceCompatibilityHelper.getAllRequiredGuides(context)
        if (items.isEmpty()) return false
        pendingGuideIds = items.map { it.id.name }.toTypedArray()
        pendingStartProjectIds = selectedIds.toIntArray()
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                isStarOrderLoading = starOrderState is StarOrderUiState.Loading || starOrderState is StarOrderUiState.Success,
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
                    if (!showCompatibilityGuideIfNeeded(selectedIds)) {
                        startService(selectedIds)
                    }
                }
            )
        }

        if (pendingStartProjectIds != null) {
            val recommendedIds = pendingGuideIds.toSet()
            val guideItems = remember(context, orderKey, pendingGuideIds) {
                DeviceCompatibilityHelper.getAllGuides(context).filter { it.id.name in recommendedIds }
            }
            BackgroundSettingsDialog(
                initialItems = guideItems,
                refreshItems = {
                    DeviceCompatibilityHelper.getAllGuides(context).filter { it.id.name in recommendedIds }
                },
                dismissLabelRes = CoreUiR.string.common_cancel,
                onItemAction = { DeviceCompatibilityHelper.openGuide(context, it) },
                onDismiss = ::dismissPendingStart,
                onConfirm = ::confirmPendingStart,
            )
        }
    }
}
