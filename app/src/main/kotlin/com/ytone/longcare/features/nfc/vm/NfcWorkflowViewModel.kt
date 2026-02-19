package com.ytone.longcare.features.nfc.vm

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import com.ytone.longcare.navigation.SignInMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NFC签到页面的ViewModel
 */
@HiltViewModel
class NfcWorkflowViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val appEventBus: AppEventBus,
    private val nfcManager: NfcManager,
    private val locationFacade: LocationFacade,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val countdownNotificationManager: CountdownNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    val uiState: StateFlow<NfcSignInUiState> = _uiState.asStateFlow()

    private val _pendingNfcData = MutableStateFlow<PendingNfcData?>(null)
    val pendingNfcData: StateFlow<PendingNfcData?> = _pendingNfcData.asStateFlow()

    private val orderDelegate = NfcOrderWorkflowDelegate(
        context = context,
        orderRepository = orderRepository,
        toastHelper = toastHelper,
        unifiedOrderRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        countdownNotificationManager = countdownNotificationManager,
        scope = viewModelScope,
        uiState = _uiState,
    )

    private val activityAndLocationDelegate = NfcActivityAndLocationDelegate(
        context = context,
        nfcManager = nfcManager,
        locationFacade = locationFacade,
    )

    private val scanDelegate = NfcScanWorkflowDelegate(
        appEventBus = appEventBus,
        unifiedOrderRepository = unifiedOrderRepository,
        orderRepository = orderRepository,
        scope = viewModelScope,
        uiState = _uiState,
        pendingNfcData = _pendingNfcData,
        orderDelegate = orderDelegate,
    )

    private fun launchOrderDelegateAction(action: suspend NfcOrderWorkflowDelegate.() -> Unit) {
        viewModelScope.launch { orderDelegate.action() }
    }

    fun startOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        longitude: String = "",
        latitude: String = ""
    ) = launchOrderDelegateAction {
        startOrder(orderKey, nfcDeviceId, longitude, latitude)
    }

    fun endOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ) = launchOrderDelegateAction {
        endOrder(
            orderKey = orderKey,
            nfcDeviceId = nfcDeviceId,
            projectIdList = projectIdList,
            beginImgList = beginImgList,
            endImageList = endImageList,
            centerImgList = centerImgList,
            longitude = longitude,
            latitude = latitude,
            endType = endType
        )
    }

    fun confirmEndOrder(params: EndOrderParams) = launchOrderDelegateAction { confirmEndOrder(params) }

    fun cancelEndOrder() = orderDelegate.cancelEndOrder()

    fun resetState() = orderDelegate.resetState()

    fun showError(message: String) = orderDelegate.showError(message)

    fun buildServiceCompleteDataFromCache(
        orderKey: OrderKey,
        endOderInfo: EndOderInfo?,
        trueServiceTime: Int
    ): ServiceCompleteData = orderDelegate.buildServiceCompleteDataFromCache(orderKey, endOderInfo, trueServiceTime)

    fun isNfcSupported(): Boolean = activityAndLocationDelegate.isNfcSupported()

    fun enableNfcForActivity(activity: Activity) = activityAndLocationDelegate.enableNfcForActivity(activity)

    fun disableNfcForActivity(activity: Activity) = activityAndLocationDelegate.disableNfcForActivity(activity)

    suspend fun getCurrentLocationCoordinates(): Pair<String, String> = activityAndLocationDelegate.getCurrentLocationCoordinates()

    fun observeNfcEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> Pair<String, String>
    ) = scanDelegate.observeNfcEvents(orderKey, signInMode, endOderInfo, onLocationRequest)

    fun confirmLocationActivation(data: PendingNfcData) = scanDelegate.confirmLocationActivation(data)

    fun cancelLocationActivation() = scanDelegate.cancelLocationActivation()

    fun mockNfcScan(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?
    ) = scanDelegate.mockNfcScan(orderKey, signInMode, endOderInfo)

    override fun onCleared() {
        scanDelegate.clear()
        super.onCleared()
    }
}
