package com.ytone.longcare.features.sales

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val locationFacade: LocationFacade,
    private val cosRepository: CosRepository,
    private val qlzSdkClient: QlzSdkClient,
    private val systemConfigManager: SystemConfigManager,
    @param:ApplicationContext private val applicationContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()
    private var customerSearchJob: Job? = null
    private var customerSearchRequestId = 0L
    private var customerDetailJob: Job? = null
    private var customerDetailRequestId = 0L
    private var toDoCountJob: Job? = null
    private var toDoCountRequestId = 0L
    private var toDoListJob: Job? = null
    private var toDoListRequestId = 0L
    private var sdkTokenRecoveryAttempted = false

    init {
        loadCompanyName()
        loadRecentCustomers()
        refreshDeviceState()
    }

    fun loadRecentCustomers() {
        execute(
            operation = "正在加载销售计划",
            request = saleRepository::getRecentUserLatentList,
            onSuccess = { customers ->
                _uiState.value =
                    _uiState.value.copy(
                        recentCustomers = customers,
                    )
            },
        )
    }

    fun loadToDoCount() {
        val requestId = ++toDoCountRequestId
        toDoCountJob?.cancel()
        _uiState.value =
            _uiState.value.copy(
                isToDoCountLoading = true,
                toDoCountErrorMessage = null,
            )
        toDoCountJob =
            viewModelScope.launch {
                try {
                    when (val result = saleRepository.getToDoCount()) {
                        is ApiResult.Success -> {
                            if (requestId == toDoCountRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        toDoCount = result.data.num.coerceAtLeast(0),
                                    )
                            }
                        }

                        is ApiResult.Failure -> {
                            if (requestId == toDoCountRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        toDoCountErrorMessage =
                                            result.message.ifBlank {
                                                "待办数量加载失败"
                                            },
                                    )
                            }
                        }

                        is ApiResult.Exception -> {
                            if (requestId == toDoCountRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        toDoCountErrorMessage =
                                            result.exception.message
                                                ?: "待办数量加载失败",
                                    )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (requestId == toDoCountRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                toDoCountErrorMessage =
                                    throwable.message ?: "待办数量加载失败",
                            )
                    }
                } finally {
                    if (requestId == toDoCountRequestId) {
                        _uiState.value =
                            _uiState.value.copy(isToDoCountLoading = false)
                    }
                }
            }
    }

    fun loadToDoList() {
        val requestId = ++toDoListRequestId
        toDoListJob?.cancel()
        _uiState.value =
            _uiState.value.copy(
                isToDoListLoading = true,
                toDoListErrorMessage = null,
            )
        toDoListJob =
            viewModelScope.launch {
                try {
                    when (val result = saleRepository.getToDoList()) {
                        is ApiResult.Success -> {
                            if (requestId == toDoListRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(toDoItems = result.data)
                            }
                        }

                        is ApiResult.Failure -> {
                            if (requestId == toDoListRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        toDoListErrorMessage =
                                            result.message.ifBlank {
                                                "待办事项加载失败"
                                            },
                                    )
                            }
                        }

                        is ApiResult.Exception -> {
                            if (requestId == toDoListRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        toDoListErrorMessage =
                                            result.exception.message
                                                ?: "待办事项加载失败",
                                    )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (requestId == toDoListRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                toDoListErrorMessage =
                                    throwable.message ?: "待办事项加载失败",
                            )
                    }
                } finally {
                    if (requestId == toDoListRequestId) {
                        _uiState.value =
                            _uiState.value.copy(isToDoListLoading = false)
                    }
                }
            }
    }

    fun searchCustomers(
        keyword: String,
        checkState: Int,
    ) {
        val normalizedKeyword = keyword.trim()
        val normalizedCheckState =
            if (normalizedKeyword.isNotEmpty()) {
                UserLatentCheckState.ALL
            } else {
                checkState
            }
        val requestId = ++customerSearchRequestId
        customerSearchJob?.cancel()
        _uiState.value =
            _uiState.value.copy(
                customerSearchKeyword = keyword,
                customerCheckState = normalizedCheckState,
                isCustomerListLoading = true,
                isCustomerListLoadingMore = false,
                customerPageIndex = 0,
                canLoadMoreCustomers = true,
                customerLoadMoreErrorMessage = null,
                errorMessage = null,
            )
        customerSearchJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            saleRepository.searchUserLatentList(
                                SearchUserLatentParamModel(
                                    pageIndex = FIRST_CUSTOMER_PAGE,
                                    userName = normalizedKeyword,
                                    checkState = normalizedCheckState,
                                )
                            )
                    ) {
                        is ApiResult.Success -> {
                            if (requestId == customerSearchRequestId) {
                                val customers = result.data.distinctBy { it.id }
                                _uiState.value =
                                    _uiState.value.copy(
                                        customers = customers,
                                        customerPageIndex = FIRST_CUSTOMER_PAGE,
                                        canLoadMoreCustomers = result.data.isNotEmpty(),
                                    )
                            }
                        }

                        is ApiResult.Failure -> {
                            if (requestId == customerSearchRequestId) {
                                showError(result.message)
                            }
                        }

                        is ApiResult.Exception -> {
                            if (requestId == customerSearchRequestId) {
                                showError(
                                    result.exception.message
                                        ?: "网络异常，请稍后重试"
                                )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (requestId == customerSearchRequestId) {
                        showError(
                            throwable.message ?: "网络异常，请稍后重试"
                        )
                    }
                } finally {
                    if (requestId == customerSearchRequestId) {
                        _uiState.value =
                            _uiState.value.copy(isCustomerListLoading = false)
                    }
                }
            }
    }

    fun loadNextCustomerPage() {
        val currentState = _uiState.value
        if (
            currentState.isCustomerListLoading ||
            currentState.isCustomerListLoadingMore ||
            !currentState.canLoadMoreCustomers ||
            currentState.customerPageIndex < FIRST_CUSTOMER_PAGE
        ) {
            return
        }

        val requestId = customerSearchRequestId
        val nextPageIndex = currentState.customerPageIndex + 1
        _uiState.value =
            currentState.copy(
                isCustomerListLoadingMore = true,
                customerLoadMoreErrorMessage = null,
            )
        customerSearchJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            saleRepository.searchUserLatentList(
                                SearchUserLatentParamModel(
                                    pageIndex = nextPageIndex,
                                    userName = currentState.customerSearchKeyword.trim(),
                                    checkState = currentState.customerCheckState,
                                )
                            )
                    ) {
                        is ApiResult.Success -> {
                            if (requestId == customerSearchRequestId) {
                                val existingCustomers = _uiState.value.customers
                                val mergedCustomers =
                                    (existingCustomers + result.data)
                                        .distinctBy { it.id }
                                val hasNewCustomers =
                                    mergedCustomers.size > existingCustomers.size
                                _uiState.value =
                                    _uiState.value.copy(
                                        customers = mergedCustomers,
                                        customerPageIndex = nextPageIndex,
                                        canLoadMoreCustomers =
                                            result.data.isNotEmpty() && hasNewCustomers,
                                        customerLoadMoreErrorMessage = null,
                                    )
                            }
                        }

                        is ApiResult.Failure -> {
                            if (requestId == customerSearchRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        customerLoadMoreErrorMessage =
                                            result.message.ifBlank {
                                                "更多客户加载失败"
                                            },
                                    )
                            }
                        }

                        is ApiResult.Exception -> {
                            if (requestId == customerSearchRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        customerLoadMoreErrorMessage =
                                            result.exception.message
                                                ?: "更多客户加载失败",
                                    )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (requestId == customerSearchRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                customerLoadMoreErrorMessage =
                                    throwable.message ?: "更多客户加载失败",
                            )
                    }
                } finally {
                    if (requestId == customerSearchRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                isCustomerListLoadingMore = false,
                            )
                    }
                }
            }
    }

    fun loadCustomerDetail(customerId: Int) {
        val requestId = ++customerDetailRequestId
        customerDetailJob?.cancel()
        if (customerId <= 0) {
            _uiState.value =
                _uiState.value.copy(
                    selectedCustomerId = customerId,
                    selectedCustomer = null,
                    isCustomerDetailLoading = false,
                    customerDetailErrorMessage = "客户信息无效，请返回后重试",
                )
            return
        }
        _uiState.value =
            _uiState.value.copy(
                selectedCustomerId = customerId,
                selectedCustomer = null,
                isCustomerDetailLoading = true,
                customerDetailErrorMessage = null,
            )
        customerDetailJob =
            viewModelScope.launch {
                try {
                    when (
                        val result =
                            saleRepository.getUserLatentDetail(customerId)
                    ) {
                        is ApiResult.Success -> {
                            if (requestId == customerDetailRequestId) {
                                val detail = result.data
                                _uiState.value =
                                    if (detail.id == customerId) {
                                        _uiState.value.copy(
                                            selectedCustomer = detail,
                                            customerDetailErrorMessage = null,
                                        )
                                    } else {
                                        _uiState.value.copy(
                                            selectedCustomer = null,
                                            customerDetailErrorMessage =
                                                "客户详情数据异常，请重试",
                                        )
                                    }
                            }
                        }

                        is ApiResult.Failure -> {
                            if (requestId == customerDetailRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        customerDetailErrorMessage =
                                            result.message.ifBlank {
                                                "客户详情加载失败，请重试"
                                            },
                                    )
                            }
                        }

                        is ApiResult.Exception -> {
                            if (requestId == customerDetailRequestId) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        customerDetailErrorMessage =
                                            result.exception.message
                                                ?.takeIf(String::isNotBlank)
                                                ?: "客户详情加载失败，请重试",
                                    )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (requestId == customerDetailRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                customerDetailErrorMessage =
                                    throwable.message
                                        ?.takeIf(String::isNotBlank)
                                        ?: "客户详情加载失败，请重试",
                            )
                    }
                } finally {
                    if (requestId == customerDetailRequestId) {
                        _uiState.value =
                            _uiState.value.copy(
                                isCustomerDetailLoading = false,
                            )
                    }
                }
            }
    }

    fun retryCustomerDetail() {
        loadCustomerDetail(_uiState.value.selectedCustomerId)
    }

    fun selectCustomer(customerId: Int) {
        _uiState.value = _uiState.value.copy(selectedCustomerId = customerId)
    }

    fun requestCurrentLocation() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    operation = "正在获取当前位置",
                    errorMessage = null,
                )
            try {
                val location = locationFacade.getFreshLocation()
                if (location == null) {
                    showError("定位失败，请检查定位服务后重试")
                } else {
                    _uiState.value =
                        _uiState.value.copy(
                            currentLocation = location,
                            noticeMessage = "定位成功",
                        )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "定位失败，请稍后重试")
            } finally {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        operation = "",
                    )
            }
        }
    }

    fun onLocationPermissionGranted() {
        locationFacade.notifyPermissionGranted()
        requestCurrentLocation()
    }

    fun submitCustomer(
        draft: SalesCustomerDraft,
        photoUris: List<Uri>,
    ) {
        draft.validationMessage()?.let {
            showError(it)
            return
        }
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    operation =
                        if (photoUris.isEmpty()) {
                            "正在提交客户信息"
                        } else {
                            "正在上传照片"
                        },
                    errorMessage = null,
                    submissionResult = null,
                )
            try {
                val uploadedKeys =
                    uploadPhotoKeys(photoUris.take(MAX_SALES_CUSTOMER_PHOTOS))
                _uiState.value =
                    _uiState.value.copy(operation = "正在提交客户信息")
                when (
                    val result =
                        saleRepository.addUserLatent(
                            draft.toRequest(
                                location = _uiState.value.currentLocation,
                                photoKeys = uploadedKeys,
                            )
                        )
                ) {
                    is ApiResult.Success -> onCustomerSubmitted(result.data)
                    is ApiResult.Failure -> showError(result.message)
                    is ApiResult.Exception ->
                        showError(
                            result.exception.message ?: "提交失败，请稍后重试"
                        )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "提交失败，请稍后重试")
            } finally {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        operation = "",
                    )
            }
        }
    }

    fun prepareEvaluation(customerId: Int) {
        if (customerId <= 0) {
            showError("请先选择需要评估的客户")
            return
        }
        sdkTokenRecoveryAttempted = false
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    operation = "正在准备评估",
                    errorMessage = null,
                    selectedCustomerId = customerId,
                    evaluationCompleted = null,
                    sdkProgressText = "",
                    checkToken = null,
                )
            try {
                val sdkDeviceId =
                    qlzSdkClient.getDeviceId().getOrElse { throwable ->
                        throw IllegalStateException(
                            "检测设备准备失败，请稍后重试",
                            throwable,
                        )
                    }
                _uiState.value =
                    _uiState.value.copy(
                        sdkDeviceId = sdkDeviceId,
                        connectedDeviceName = qlzSdkClient.getConnectedDeviceName(),
                        operation = "正在准备评估",
                    )
                when (
                    val result =
                        saleRepository.getCheckToken(
                            customerId = customerId,
                            checkDeviceId = sdkDeviceId,
                        )
                ) {
                    is ApiResult.Success ->
                        _uiState.value =
                            _uiState.value.copy(
                                checkToken = result.data,
                                connectedDeviceName =
                                    qlzSdkClient.getConnectedDeviceName(),
                            )

                    is ApiResult.Failure -> showError(result.message)
                    is ApiResult.Exception ->
                        showError(
                            "评估准备失败，请稍后重试"
                        )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                showError("评估准备失败，请稍后重试")
            } finally {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        operation = "",
                    )
            }
        }
    }

    fun refreshDeviceState() {
        _uiState.value =
            _uiState.value.copy(
                connectedDeviceName = qlzSdkClient.getConnectedDeviceName(),
            )
    }

    fun launchSdk(activity: Activity) {
        val token = _uiState.value.checkToken?.token.orEmpty()
        if (token.isBlank()) {
            showError("评估尚未准备完成，请稍后重试")
            return
        }
        openSdkWithToken(activity, token)
    }

    private fun openSdkWithToken(
        activity: Activity,
        token: String,
    ) {
        qlzSdkClient.openByToken(
            activity = activity,
            token = token,
            onEvent = { event ->
                viewModelScope.launch {
                    onSdkEvent(event, activity)
                }
            },
        )
    }

    fun requiredSdkPermissions(): Array<String> =
        qlzSdkClient.requiredRuntimePermissions()

    fun openLatestReport(activity: Activity) {
        val reportUrl =
            _uiState.value.evaluationCompleted?.reportUrl
                .orEmpty()
                .ifBlank { _uiState.value.selectedCustomer?.pgUrl.orEmpty() }
        if (reportUrl.isBlank()) {
            showError("当前客户暂无可查看的评估报告")
            return
        }
        qlzSdkClient.openReport(activity, reportUrl)
    }

    fun openReportUrl(
        activity: Activity,
        reportUrl: String,
    ) {
        if (reportUrl.isBlank()) {
            showError("评估页面地址为空，请稍后重试")
            return
        }
        qlzSdkClient.openReport(activity, reportUrl)
    }

    fun clearTransientMessage() {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = null,
                noticeMessage = null,
            )
    }

    fun resetSubmission() {
        _uiState.value =
            _uiState.value.copy(
                submissionResult = null,
                currentLocation = null,
            )
    }

    private fun loadCompanyName() {
        viewModelScope.launch {
            val companyName =
                systemConfigManager.refreshCompanyName()?.trim().orEmpty()
                    .ifEmpty { systemConfigManager.getCompanyName() }
            _uiState.value = _uiState.value.copy(companyName = companyName)
        }
    }

    private suspend fun uploadPhotoKeys(photoUris: List<Uri>): List<String> {
        if (photoUris.isEmpty()) {
            return emptyList()
        }
        return photoUris.mapIndexed { index, uri ->
            _uiState.value =
                _uiState.value.copy(
                    operation = "正在上传照片 ${index + 1}/${photoUris.size}"
                )
            val result =
                cosRepository.uploadFile(
                    CosUtils.createUploadParams(
                        context = applicationContext,
                        fileUri = uri,
                        folderType = CosConstants.DEFAULT_FOLDER_TYPE,
                    )
                )
            val uploadedKey = result.key
            if (!result.success || uploadedKey.isNullOrBlank()) {
                throw IllegalStateException(
                    "第 ${index + 1} 张照片上传失败，请稍后重试"
                )
            }
            uploadedKey
        }
    }

    private fun onCustomerSubmitted(result: AddUserLatentResultModel) {
        _uiState.value =
            _uiState.value.copy(
                submissionResult = result,
                selectedCustomerId = result.id,
                noticeMessage = "客户信息提交成功",
            )
        loadRecentCustomers()
    }

    private suspend fun onSdkEvent(
        event: QlzSdkEvent,
        activity: Activity,
    ) {
        when (event) {
            is QlzSdkEvent.Completed ->
                _uiState.value =
                    _uiState.value.copy(
                        connectedDeviceName =
                            qlzSdkClient.getConnectedDeviceName()
                                ?: _uiState.value.connectedDeviceName,
                        evaluationCompleted = event,
                        sdkProgressText = "检测完成",
                        noticeMessage = "评估成功",
                    )

            is QlzSdkEvent.Progress ->
                _uiState.value =
                    _uiState.value.copy(
                        connectedDeviceName =
                            qlzSdkClient.getConnectedDeviceName()
                                ?: _uiState.value.connectedDeviceName,
                        sdkProgressText =
                            "检测进度 ${event.successCount}/${event.totalCount}",
                    )

            is QlzSdkEvent.Error -> {
                if (event.requiresTokenRefresh) {
                    recoverSdkTokenAndRelaunch(activity)
                } else {
                    showError(
                        event.message.ifBlank {
                            "评估暂时无法继续，请稍后重试"
                        }
                    )
                }
            }

            QlzSdkEvent.Cancelled ->
                _uiState.value =
                    _uiState.value.copy(
                        connectedDeviceName = qlzSdkClient.getConnectedDeviceName(),
                        noticeMessage = "已取消本次评估",
                    )

            QlzSdkEvent.DetectionPageClosed ->
                _uiState.value =
                    _uiState.value.copy(
                        connectedDeviceName = qlzSdkClient.getConnectedDeviceName(),
                    )

            QlzSdkEvent.ReportPageClosed -> Unit
        }
    }

    private suspend fun recoverSdkTokenAndRelaunch(activity: Activity) {
        if (sdkTokenRecoveryAttempted) {
            showError("本次评估已失效，请重新进入评估页面")
            return
        }
        sdkTokenRecoveryAttempted = true

        val customerId = _uiState.value.selectedCustomerId
        if (customerId <= 0) {
            showError("客户信息无效，请重新进入评估页面")
            return
        }

        _uiState.value =
            _uiState.value.copy(
                isLoading = true,
                operation = "正在重新准备评估",
                errorMessage = null,
                checkToken = null,
            )
        try {
            val deviceId =
                _uiState.value.sdkDeviceId.ifBlank {
                    qlzSdkClient.getDeviceId().getOrElse { throwable ->
                        throw IllegalStateException(
                            "检测设备准备失败，请稍后重试",
                            throwable,
                        )
                    }
                }
            when (
                val result =
                    saleRepository.getCheckToken(
                        customerId = customerId,
                        checkDeviceId = deviceId,
                    )
            ) {
                is ApiResult.Success -> {
                    val refreshedToken = result.data.token.trim()
                    if (refreshedToken.isBlank()) {
                        showError("评估凭证获取失败，请重新进入评估页面")
                        return
                    }
                    _uiState.value =
                        _uiState.value.copy(
                            sdkDeviceId = deviceId,
                            checkToken = result.data,
                            connectedDeviceName =
                                qlzSdkClient.getConnectedDeviceName(),
                        )
                    if (activity.isFinishing || activity.isDestroyed) {
                        showError("评估页面已关闭，请重新进入")
                    } else {
                        openSdkWithToken(activity, refreshedToken)
                    }
                }

                is ApiResult.Failure -> showError(result.message)
                is ApiResult.Exception ->
                    showError("评估凭证刷新失败，请重新进入评估页面")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            showError("评估凭证刷新失败，请重新进入评估页面")
        } finally {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = false,
                    operation = "",
                )
        }
    }

    private fun <T> execute(
        operation: String,
        request: suspend () -> ApiResult<T>,
        onSuccess: (T) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    operation = operation,
                    errorMessage = null,
                )
            try {
                when (val result = request()) {
                    is ApiResult.Success -> onSuccess(result.data)
                    is ApiResult.Failure -> showError(result.message)
                    is ApiResult.Exception ->
                        showError(
                            result.exception.message ?: "网络异常，请稍后重试"
                        )
                }
            } finally {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        operation = "",
                    )
            }
        }
    }

    private fun showError(message: String) {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = message.ifBlank { "操作失败，请稍后重试" },
            )
    }

    private companion object {
        const val FIRST_CUSTOMER_PAGE = 1
    }
}

data class SalesUiState(
    val isLoading: Boolean = false,
    val isCustomerListLoading: Boolean = true,
    val isCustomerListLoadingMore: Boolean = false,
    val isCustomerDetailLoading: Boolean = false,
    val isToDoCountLoading: Boolean = false,
    val isToDoListLoading: Boolean = false,
    val operation: String = "",
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val companyName: String = "",
    val recentCustomers: List<UserLatentListModel> = emptyList(),
    val toDoCount: Int? = null,
    val toDoCountErrorMessage: String? = null,
    val toDoItems: List<ToDoResultModel> = emptyList(),
    val toDoListErrorMessage: String? = null,
    val customers: List<UserLatentListModel> = emptyList(),
    val customerSearchKeyword: String = "",
    val customerCheckState: Int = UserLatentCheckState.ALL,
    val customerPageIndex: Int = 0,
    val canLoadMoreCustomers: Boolean = true,
    val customerLoadMoreErrorMessage: String? = null,
    val selectedCustomerId: Int = 0,
    val selectedCustomer: UserLatentDetailModel? = null,
    val customerDetailErrorMessage: String? = null,
    val currentLocation: LocationResult? = null,
    val submissionResult: AddUserLatentResultModel? = null,
    val sdkDeviceId: String = "",
    val connectedDeviceName: String? = null,
    val checkToken: CheckTokenModel? = null,
    val sdkProgressText: String = "",
    val evaluationCompleted: QlzSdkEvent.Completed? = null,
)

data class SalesCustomerDraft(
    val userName: String = "",
    val identityCardNumber: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianRelation: String = "",
    val liveAddress: String = "",
) {
    fun validationMessage(): String? =
        when {
            userName.isBlank() -> "请输入老人姓名"
            identityCardNumber.length !in setOf(15, 18) ->
                "请输入正确的老人身份证号码"

            guardianName.isBlank() -> "请输入联系人"
            !guardianPhone.matches(Regex("^1[3-9]\\d{9}$")) ->
                "请输入正确的联系人手机号码"

            guardianRelation.isBlank() -> "请输入与老人关系"
            liveAddress.isBlank() -> "请输入居住地址"
            else -> null
        }

    fun toRequest(
        location: LocationResult?,
        photoKeys: List<String>,
    ): AddUserLatentParamModel =
        AddUserLatentParamModel(
            userName = userName.trim(),
            identityCardNumber = identityCardNumber.trim(),
            guardianName = guardianName.trim(),
            guardianPhone = guardianPhone.trim(),
            guardianRelation = guardianRelation.trim(),
            liveAddress = liveAddress.trim(),
            liveLng = location?.longitude?.toString().orEmpty(),
            liveLat = location?.latitude?.toString().orEmpty(),
            img1 = photoKeys.getOrNull(0).orEmpty(),
            img2 = photoKeys.getOrNull(1).orEmpty(),
            img3 = photoKeys.getOrNull(2).orEmpty(),
        )
}
