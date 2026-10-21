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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    private var pendingEndOrder: EndOrderParams? = null
    val hasPendingEndLocation: Boolean get() = pendingEndOrder != null

    suspend fun startOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        location: LocationResult
    ): Boolean {
        if (!validateLocation(location, orderKey, SignInMode.START_ORDER, "start_check")) return false
        performStartOrderWorkflow(
            orderRepository = orderRepository,
            orderKey = orderKey,
            nfcDeviceId = nfcDeviceId,
            location = location,
            uiState = uiState,
            userMessages = userMessages,
        )
        return true
    }

    suspend fun endOrder(
        orderKey: OrderKey,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        endType: Int = 1,
        requestLocation: suspend () -> LocationRequestResult = ::acquireLocation,
    ) {
        pendingEndOrder = null
        // CheckEndOrder 不接收坐标。预检和确认完成后才采集本次提交的位置。
        withEndOrderDiagnostics(orderKey, "scan") { diagnostics ->
            performEndOrderWorkflow(
                orderRepository = orderRepository,
                orderKey = orderKey,
                nfcDeviceId = nfcDeviceId,
                projectIdList = projectIdList,
                beginImgList = beginImgList,
                endImageList = endImageList,
                centerImgList = centerImgList,
                endType = endType,
                uiState = uiState,
                userMessages = userMessages,
                diagnostics = diagnostics,
                onCheckSuccess = {
                    executeEndOrder(EndOrderParams(orderKey, nfcDeviceId, projectIdList,
                        beginImgList, endImageList, centerImgList, endType = endType), requestLocation, diagnostics)
                }
            )
        }
    }

    suspend fun confirmEndOrder(
        params: EndOrderParams,
        requestLocation: suspend () -> LocationRequestResult = ::acquireLocation,
    ) {
        withEndOrderDiagnostics(params.orderKey, "confirmation") { diagnostics ->
            executeEndOrder(params, requestLocation, diagnostics)
        }
    }

    suspend fun retryEndLocation(requestLocation: suspend () -> LocationRequestResult) {
        pendingEndOrder?.let { params ->
            withEndOrderDiagnostics(params.orderKey, "location_retry") { diagnostics ->
                executeEndOrder(params, requestLocation, diagnostics)
            }
        }
    }

    fun locationPermissionDenied() {
        if (pendingEndOrder != null) uiState.value = reportedNfcError(textResolver.text(LocationFailure.PERMISSION.messageRes()))
    }

    fun cancelEndOrder() {
        pendingEndOrder = null
        uiState.value = NfcSignInUiState.Initial
    }

    fun resetState() {
        pendingEndOrder = null
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

    fun showLocationFailure(failure: LocationFailure) = showError(textResolver.text(failure.messageRes()))

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
        params: EndOrderParams,
        requestLocation: suspend () -> LocationRequestResult,
        diagnostics: EndOrderDiagnostics,
    ) {
        pendingEndOrder = params
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.FETCHING_LOCATION)
        var locationException: Exception? = null
        val result = diagnostics.measure("location") {
            requestNfcLocation(
                params.orderKey, SignInMode.END_ORDER, "end_submit_acquire", userMessages.locationUnavailable,
                userId = diagnostics.userId, reportToServer = false, attemptId = diagnostics.attemptId,
                request = {
                    try {
                        requestLocation()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        locationException = error
                        throw error
                    }
                },
            )
        }
        currentCoroutineContext().ensureActive()
        val location = when (result) {
            is LocationRequestResult.Coordinates -> result.location
            is LocationRequestResult.Error -> {
                diagnostics.captureLocation(result.location)
                diagnostics.finish("location_failure", reason = (result.reason ?: LocationFailure.UNAVAILABLE).name,
                    throwable = locationException, isError = true)
                showError(result.message, buglyAlreadyReported = true)
                return
            }
            LocationRequestResult.PermissionRequired -> {
                diagnostics.finish("permission_required")
                uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION)
                return
            }
        }
        diagnostics.captureLocation(location)
        val failure = locationFacade.serviceOrderLocationFailure(location)
        if (failure != null) {
            diagnostics.finish("location_rejected", reason = failure.name, isError = true)
            uiState.value = reportedNfcError(textResolver.text(failure.messageRes()))
            return
        }
        // 位置错误可只重试采集；请求发出后清除动作，避免网络结果未知时重复签退。
        pendingEndOrder = null
        uiState.value = NfcSignInUiState.Loading(NfcLoadingReason.SUBMITTING)
        executeEndOrderRequest(
            serviceOrderLifecycle = serviceOrderLifecycle,
            orderRepository = orderRepository,
            completionDelegate = completionDelegate,
            uiState = uiState,
            orderKey = params.orderKey,
            nfcDeviceId = params.nfcDeviceId,
            projectIdList = params.porjectIdList,
            beginImgList = params.beginImgList,
            endImageList = params.endImageList,
            centerImgList = params.centerImgList,
            location = location,
            endType = params.endType,
            userMessages = userMessages,
            diagnostics = diagnostics,
        )
    }

    private fun validateLocation(location: LocationResult, orderKey: OrderKey, mode: SignInMode, stage: String): Boolean {
        val failure = locationFacade.serviceOrderLocationFailure(location)
        val usable = failure == null
        trackNfcLocation(
            if (usable) "nfc_location_validated" else "nfc_location_rejected",
            orderKey, mode, stage, location = location, isError = !usable,
            reason = failure?.name,
        )
        if (failure != null) uiState.value = reportedNfcError(textResolver.text(failure.messageRes()))
        return usable
    }

    private suspend fun acquireLocation(): LocationRequestResult =
        NfcLocationDelegate(locationFacade, textResolver).acquireLocation()
}
