package com.ytone.longcare.features.nfc.vm

import android.content.Context
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class NfcOrderWorkflowDelegate(
    private val context: Context,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val countdownNotificationManager: CountdownNotificationManager,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
) {
    private val completionDelegate = NfcOrderCompletionDelegate(
        context = context,
        unifiedOrderRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        countdownNotificationManager = countdownNotificationManager,
        scope = scope
    )

    suspend fun startOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        longitude: String = "",
        latitude: String = ""
    ) = performStartOrderWorkflow(
        orderRepository = orderRepository,
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        longitude = longitude,
        latitude = latitude,
        toastHelper = toastHelper,
        uiState = uiState
    )

    suspend fun endOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ) = performEndOrderWorkflow(
        orderRepository = orderRepository,
        orderKey = orderKey,
        nfcDeviceId = nfcDeviceId,
        projectIdList = projectIdList,
        beginImgList = beginImgList,
        endImageList = endImageList,
        centerImgList = centerImgList,
        longitude = longitude,
        latitude = latitude,
        endType = endType,
        toastHelper = toastHelper,
        uiState = uiState,
        onCheckSuccess = {
            executeEndOrder(
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
    )

    suspend fun confirmEndOrder(params: EndOrderParams) {
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
        executeEndOrder(
            orderKey = params.orderKey,
            nfcDeviceId = params.nfcDeviceId,
            projectIdList = params.porjectIdList,
            beginImgList = params.beginImgList,
            endImageList = params.endImageList,
            centerImgList = params.centerImgList,
            longitude = params.longitude,
            latitude = params.latitude,
            endType = params.endType
        )
    }

    fun cancelEndOrder() {
        uiState.value = NfcSignInUiState.Initial
    }

    fun resetState() {
        uiState.value = NfcSignInUiState.Initial
    }

    fun showError(message: String) {
        uiState.value = NfcSignInUiState.Error(message)
    }

    fun buildServiceCompleteDataFromCache(
        orderKey: OrderKey,
        endOderInfo: EndOderInfo?,
        trueServiceTime: Int
    ): ServiceCompleteData = completionDelegate.buildServiceCompleteDataFromCache(
        orderKey = orderKey,
        endOderInfo = endOderInfo,
        trueServiceTime = trueServiceTime
    )

    private suspend fun executeEndOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String>,
        longitude: String,
        latitude: String,
        endType: Int
    ) = executeEndOrderRequest(
        orderRepository = orderRepository,
        toastHelper = toastHelper,
        completionDelegate = completionDelegate,
        uiState = uiState,
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
