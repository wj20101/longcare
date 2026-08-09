package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal fun applyUserVisibleNfcError(
    uiState: MutableStateFlow<NfcSignInUiState>,
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    buglyAlreadyReported: Boolean = false,
    extras: Map<String, Any?> = emptyMap(),
    reporter: (NfcUserVisibleErrorReport) -> Unit = ::sendNfcUserVisibleErrorReport,
) {
    uiState.value = if (buglyAlreadyReported) {
        reportedNfcError(message)
    } else {
        reportUserVisibleNfcError(
            message = message,
            source = source,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = extras,
            reporter = reporter,
        )
    }
}

internal class NfcOrderWorkflowDelegate(
    private val orderRepository: OrderRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val serviceCountdownSystemGateway: ServiceCountdownSystemGateway,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
) {
    private val completionDelegate = NfcOrderCompletionDelegate(
        unifiedOrderRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        serviceCountdownSystemGateway = serviceCountdownSystemGateway,
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

    fun showError(
        message: String,
        source: String = "nfc_order_workflow_show_error",
        orderKey: OrderKey? = null,
        signInMode: SignInMode? = null,
        nfcDeviceId: String? = null,
        buglyAlreadyReported: Boolean = false,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        applyUserVisibleNfcError(
            uiState = uiState,
            message = message,
            source = source,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            buglyAlreadyReported = buglyAlreadyReported,
            extras = extras,
        )
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
