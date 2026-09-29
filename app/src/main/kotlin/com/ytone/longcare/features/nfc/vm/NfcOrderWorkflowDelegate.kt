package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.messageRes
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import com.ytone.longcare.navigation.SignInMode
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
    private val locationFacade: LocationFacade,
    private val textResolver: ResourceTextResolver,
    private val serviceOrderLifecycle: ServiceOrderLifecycle,
    private val orderRepository: OrderRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val serviceCountdownSystemGateway: ServiceCountdownSystemGateway,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
    private val userMessages: NfcUserMessages,
) {
    private val completionDelegate = NfcOrderCompletionDelegate(
        unifiedOrderRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        serviceCountdownSystemGateway = serviceCountdownSystemGateway,
    )

    suspend fun startOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        location: LocationResult
    ) {
        if (!validateLocation(location)) return
        performStartOrderWorkflow(
            orderRepository = orderRepository,
            orderKey = orderKey,
            nfcDeviceId = nfcDeviceId,
            longitude = location.longitude.toString(),
            latitude = location.latitude.toString(),
            uiState = uiState,
            userMessages = userMessages,
        )
    }

    suspend fun endOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        location: LocationResult,
        endType: Int = 1
    ) {
        if (!validateLocation(location)) return
        performEndOrderWorkflow(
            orderRepository = orderRepository,
            orderKey = orderKey,
            nfcDeviceId = nfcDeviceId,
            projectIdList = projectIdList,
            beginImgList = beginImgList,
            endImageList = endImageList,
            centerImgList = centerImgList,
            location = location,
            endType = endType,
            uiState = uiState,
            userMessages = userMessages,
            onCheckSuccess = {
                executeEndOrder(
                    orderKey = orderKey,
                    nfcDeviceId = nfcDeviceId,
                    projectIdList = projectIdList,
                    beginImgList = beginImgList,
                    endImageList = endImageList,
                    centerImgList = centerImgList,
                    location = location,
                    endType = endType
                )
            }
        )
    }

    suspend fun confirmEndOrder(params: EndOrderParams) {
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
        executeEndOrder(
            orderKey = params.orderKey,
            nfcDeviceId = params.nfcDeviceId,
            projectIdList = params.porjectIdList,
            beginImgList = params.beginImgList,
            endImageList = params.endImageList,
            centerImgList = params.centerImgList,
            location = params.location,
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
        location: LocationResult,
        endType: Int
    ) {
        if (!validateLocation(location)) return
        executeEndOrderRequest(
            serviceOrderLifecycle = serviceOrderLifecycle,
            orderRepository = orderRepository,
            completionDelegate = completionDelegate,
            uiState = uiState,
            orderKey = orderKey,
            nfcDeviceId = nfcDeviceId,
            projectIdList = projectIdList,
            beginImgList = beginImgList,
            endImageList = endImageList,
            centerImgList = centerImgList,
            longitude = location.longitude.toString(),
            latitude = location.latitude.toString(),
            endType = endType,
            userMessages = userMessages,
        )
    }

    private fun validateLocation(location: LocationResult): Boolean {
        if (locationFacade.isUsable(location)) return true
        uiState.value = NfcSignInUiState.Error(textResolver.text(LocationFailure.QUALITY.messageRes()))
        return false
    }
}
