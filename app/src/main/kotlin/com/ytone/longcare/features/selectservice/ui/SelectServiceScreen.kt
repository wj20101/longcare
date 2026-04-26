package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.BatteryGuideStep
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.PermissionGuideType
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
    val context = LocalContext.current
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

    var pendingStartProjectIds by remember { mutableStateOf<List<Int>?>(null) }
    var showPopupPermissionDialog by remember { mutableStateOf(false) }
    var popupPermissionMessage by remember { mutableStateOf("") }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var batteryDialogTitle by remember { mutableStateOf("") }
    var batteryMessage by remember { mutableStateOf("") }
    var batteryConfirmLabel by remember { mutableStateOf("去设置") }
    var batteryGuideStep by remember { mutableStateOf(BatteryGuideStep.NONE) }

    fun startService(selectedIds: List<Int>) {
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

    fun continuePendingStart() {
        val selectedIds = pendingStartProjectIds
        pendingStartProjectIds = null
        if (selectedIds != null) {
            startService(selectedIds)
        }
    }

    fun showCompatibilityGuideIfNeeded(selectedIds: List<Int>): Boolean {
        return when (DeviceCompatibilityHelper.getRequiredPermissionGuide(context)) {
            PermissionGuideType.OVERLAY -> {
                pendingStartProjectIds = selectedIds
                batteryGuideStep = BatteryGuideStep.NONE
                showBatteryDialog = false
                popupPermissionMessage = """
                    为保证服务结束时能弹出全屏提醒，请开启悬浮窗权限：

                    点击「去设置」后，找到本应用并开启「显示在其他应用上层」权限。
                """.trimIndent()
                showPopupPermissionDialog = true
                true
            }

            PermissionGuideType.MANUFACTURER_POPUP -> {
                pendingStartProjectIds = selectedIds
                batteryGuideStep = BatteryGuideStep.NONE
                showBatteryDialog = false
                popupPermissionMessage = DeviceCompatibilityHelper.getPopupPermissionGuideMessage().orEmpty()
                val shouldShow = popupPermissionMessage.isNotBlank()
                showPopupPermissionDialog = shouldShow
                shouldShow
            }

            PermissionGuideType.BATTERY -> {
                val step = DeviceCompatibilityHelper.getBatteryGuideStep(context)
                if (step == BatteryGuideStep.NONE) {
                    false
                } else {
                    pendingStartProjectIds = selectedIds
                    showPopupPermissionDialog = false
                    batteryGuideStep = step
                    batteryDialogTitle = DeviceCompatibilityHelper.getBatteryGuideDialogTitle(step)
                    batteryMessage = DeviceCompatibilityHelper.getBatteryGuideMessage(context, step)
                    batteryConfirmLabel = DeviceCompatibilityHelper.getBatteryGuideConfirmLabel(step)
                    showBatteryDialog = true
                    true
                }
            }

            PermissionGuideType.FULL_SCREEN_INTENT,
            PermissionGuideType.NONE -> false
        }
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
                    if (!showCompatibilityGuideIfNeeded(selectedIds)) {
                        startService(selectedIds)
                    }
                }
            )
        }

        if (showPopupPermissionDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPopupPermissionDialog = false
                    continuePendingStart()
                },
                title = { Text("开启弹窗权限") },
                text = { Text(popupPermissionMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showPopupPermissionDialog = false
                        pendingStartProjectIds = null
                        val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)
                        val intent = if (DeviceCompatibilityHelper.needsSpecialAdaptation()) {
                            DeviceCompatibilityHelper.getPopupPermissionIntent(context)
                        } else {
                            DeviceCompatibilityHelper.getOverlayPermissionIntent(context)
                        }
                        DeviceCompatibilityHelper.safeStartActivity(context, intent)
                    }) {
                        Text("去设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPopupPermissionDialog = false
                        val currentGuide = DeviceCompatibilityHelper.getRequiredPermissionGuide(context)
                        DeviceCompatibilityHelper.markPermissionGuideShown(context, currentGuide)
                        continuePendingStart()
                    }) {
                        Text("跳过")
                    }
                }
            )
        }

        if (showBatteryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showBatteryDialog = false
                    continuePendingStart()
                },
                title = { Text(batteryDialogTitle) },
                text = { Text(batteryMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showBatteryDialog = false
                        pendingStartProjectIds = null
                        when (batteryGuideStep) {
                            BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                                DeviceCompatibilityHelper.markIgnoreBatteryOptimizationRequestAttempted(context)
                                DeviceCompatibilityHelper.safeStartActivity(
                                    context,
                                    DeviceCompatibilityHelper.getRequestIgnoreBatteryOptimizationIntent(context)
                                )
                            }

                            BatteryGuideStep.OPEN_BATTERY_SETTINGS -> {
                                DeviceCompatibilityHelper.safeStartActivity(
                                    context,
                                    DeviceCompatibilityHelper.getBatteryOptimizationIntent(context)
                                )
                            }

                            BatteryGuideStep.OPEN_AUTO_START_SETTINGS -> {
                                DeviceCompatibilityHelper.markAutoStartGuideShown(context)
                                val intent =
                                    DeviceCompatibilityHelper.getAutoStartIntent(context)
                                        ?: DeviceCompatibilityHelper.getAppSettingsIntent(context)
                                DeviceCompatibilityHelper.safeStartActivity(context, intent)
                            }

                            BatteryGuideStep.NONE -> Unit
                        }
                    }) {
                        Text(batteryConfirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBatteryDialog = false
                        if (batteryGuideStep == BatteryGuideStep.OPEN_AUTO_START_SETTINGS) {
                            DeviceCompatibilityHelper.markAutoStartGuideShown(context)
                        }
                        continuePendingStart()
                    }) {
                        Text("我知道了")
                    }
                }
            )
        }
    }
}
