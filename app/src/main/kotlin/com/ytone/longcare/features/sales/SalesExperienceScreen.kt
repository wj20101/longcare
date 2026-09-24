package com.ytone.longcare.features.sales

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.AdaptiveAppNavigationScaffold
import com.ytone.longcare.features.home.ui.AppNavigationItem
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUploadContext
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.platform.sales.rememberSalesSdkUiController
import com.ytone.longcare.navigation.AppNavigator
import com.ytone.longcare.navigation.SalesRoute
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.presentation.sales.SalesPage
import kotlinx.coroutines.launch

@Composable
internal fun SalesExperienceScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel,
    navigator: AppNavigator,
    route: SalesRoute? = null,
    viewModel: SalesViewModel = hiltViewModel(),
    sdkUiController: com.ytone.longcare.platform.sales.SalesSdkUiController = rememberSalesSdkUiController(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    val capturedImageUri by actions.capturedImageUriFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val evaluationState by sdkUiController.uiState.collectAsStateWithLifecycle()
    val sdkPermissions = remember(sdkUiController) {
        sdkUiController.requiredRuntimePermissions()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var rootTab by rememberSaveable { mutableStateOf(0) }
    var registrationDraft by rememberSaveable(stateSaver = salesCustomerDraftSaver) {
        mutableStateOf(route?.draft ?: SalesCustomerDraft())
    }
    var photoUriStrings by rememberSaveable {
        mutableStateOf(route?.photos ?: emptyList<String>())
    }
    var showCameraPurposeNotice by rememberSaveable { mutableStateOf(false) }

    var measurementVisible by rememberSaveable { mutableStateOf(false) }
    val currentPage = if (route?.page == SalesPage.DEVICE_STATUS && measurementVisible) {
        SalesPage.EVALUATION_GUIDE
    } else route?.page ?: SalesPage.HOME
    val photoUris = photoUriStrings.map(String::toUri)
    val selectCustomerMessage = stringResource(R.string.sales_error_select_customer)
    val evaluationPermissionMessage =
        stringResource(R.string.sales_error_evaluation_permission)
    val locationPermissionMessage =
        stringResource(R.string.sales_error_location_permission)
    val openEvaluationErrorMessage =
        stringResource(R.string.sales_error_open_evaluation)
    val noReportMessage = stringResource(R.string.sales_error_no_report)
    val reportUrlEmptyMessage = stringResource(R.string.sales_error_report_url_empty)
    val evaluationFormTitle = stringResource(R.string.sales_evaluation_form_title)
    val evaluationReportTitle = stringResource(R.string.sales_evaluation_report_title)
    val cameraUnavailableMessage =
        stringResource(R.string.sales_error_camera_unavailable)
    val cameraPermissionMessage =
        stringResource(R.string.sales_error_camera_permission)
    val cameraPermissionPurpose =
        stringResource(R.string.sales_camera_permission_purpose)
    val salesWatermarkTitle = stringResource(R.string.sales_watermark_title)
    val unknownAdvisorName = stringResource(R.string.sales_watermark_unknown_advisor)
    val navigationItems =
        listOf(
            AppNavigationItem(stringResource(R.string.sales_nav_home)),
            AppNavigationItem(stringResource(R.string.sales_nav_customers)),
            AppNavigationItem(stringResource(R.string.sales_nav_profile)),
        )

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigate(page: SalesPage) {
        navigator.navigateWhenResumed(SalesRoute(page))
    }

    fun evaluationUploadContext(): QlzEvaluationUploadContext {
        return uiState.toQlzEvaluationUploadContext(route?.address.orEmpty()).let {
            it.copy(latitude = it.latitude.ifBlank { route?.latitude?.toString().orEmpty() },
                longitude = it.longitude.ifBlank { route?.longitude?.toString().orEmpty() })
        }
    }

    fun launchCustomEvaluation(hostActivity: android.app.Activity) {
        if (!currentPage.ownsQlzEvaluationSession()) return
        if (sdkUiController.prepareEvaluation(hostActivity, evaluationUploadContext(), viewModel::onSdkEvent)) {
            viewModel.requestSdkAuthorization()
        }
    }

    fun openFormEvaluation(formUrl: String) {
        val normalizedFormUrl = formUrl.trim()
        if (normalizedFormUrl.isBlank()) {
            showMessage(reportUrlEmptyMessage)
        } else {
            actions.onOpenEvaluationPage(normalizedFormUrl, evaluationFormTitle)
        }
    }

    fun openLatestReport() {
        val reportUrl = uiState.selectedCustomer.serverAssessmentReportUrl()
        if (reportUrl.isBlank()) {
            showMessage(noReportMessage)
        } else {
            actions.onOpenEvaluationReport(reportUrl, evaluationReportTitle)
        }
    }

    fun discardRegistrationPhotos() {
        if (photoUriStrings.isNotEmpty()) {
            viewModel.discardManagedPhotos(photoUriStrings.map(String::toUri))
            photoUriStrings = emptyList()
        }
    }

    fun finishSubmissionFlow() {
        navigator.popBackStack()
    }

    fun openCustomerDetail(
        customerId: Int,
    ) {
        navigator.navigateWhenResumed(SalesRoute(SalesPage.CUSTOMER_DETAIL, customerId))
    }

    fun startAutomaticEvaluation(customerId: Int) {
        if (customerId <= 0) {
            showMessage(selectCustomerMessage)
            return
        }
        navigator.navigateWhenResumed(requireNotNull(route).copy(page = SalesPage.DEVICE_STATUS))
    }

    fun back() {
        if (!navigator.canHandleCallback()) return
        if (route != null) {
            if (currentPage.ownsQlzEvaluationSession()) {
                viewModel.cancelSdkAuthorization()
                sdkUiController.cancel()
            }
            if (currentPage == SalesPage.REGISTRATION || currentPage == SalesPage.REGISTRATION_CONFIRM) {
                discardRegistrationPhotos()
                registrationDraft = SalesCustomerDraft()
            }
            navigator.popBackStack()
            return
        }
        rootTab = 0
    }

    BackHandler(
        enabled = route != null || rootTab != 0,
        onBack = ::back,
    )

    LaunchedEffect(currentPage, rootTab, lifecycleState) {
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        when {
            currentPage == SalesPage.HOME && rootTab == 0 -> viewModel.loadDashboard()

            currentPage == SalesPage.REMINDERS ->
                viewModel.loadToDoList()

        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (route?.page == SalesPage.CUSTOMER_DETAIL && navigator.canHandleCallback()) {
            viewModel.loadCustomerDetail(route.customerId)
        }
    }

    LaunchedEffect(route?.customerId) {
        when (route?.page) {
            SalesPage.DEVICE_STATUS -> if (!uiState.evaluationCompleted) viewModel.prepareEvaluation(route.customerId)
            SalesPage.EVALUATION_COMPLETE -> {
                viewModel.showEvaluationResult(route.customerId, route.recordId)
                if (uiState.evaluationResult == null) viewModel.loadEvaluationResult()
            }
            else -> Unit
        }
    }

    LaunchedEffect(capturedImageUri) {
        capturedImageUri?.let { uriString ->
            val capturedUri = uriString.toUri()
            if (photoUriStrings.size >= MAX_SALES_CUSTOMER_PHOTOS) {
                viewModel.discardManagedPhoto(capturedUri)
            } else {
                photoUriStrings =
                    mergeSalesCustomerPhotoUris(
                        existing = photoUriStrings.map(String::toUri),
                        added = listOf(capturedUri),
                    ).map { uri -> uri.toString() }
            }
            actions.clearCapturedImageUri()
        }
    }

    val sdkPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted =
                sdkPermissions.all { permission ->
                    permissions[permission] == true ||
                        ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
                }
            if (allGranted && activity != null) {
                launchCustomEvaluation(activity)
            } else {
                sdkUiController.showPermissionRequired()
                showMessage(evaluationPermissionMessage)
            }
        }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.onLocationPermissionGranted()
            } else {
                showMessage(locationPermissionMessage)
            }
        }

    fun openSalesWatermarkCamera() {
        actions.onNavigateToCamera(
            createSalesCustomerWatermarkData(
                title = salesWatermarkTitle,
                advisorName = user?.userName.orEmpty(),
                unknownAdvisorName = unknownAdvisorName,
            )
        )
    }

    val salesCameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                openSalesWatermarkCamera()
            } else {
                showMessage(cameraPermissionMessage)
            }
        }

    fun requestSalesCamera() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showMessage(cameraUnavailableMessage)
            return
        }
        if (UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
            openSalesWatermarkCamera()
        } else {
            showCameraPurposeNotice = true
        }
    }

    fun openEvaluationWithPermission() {
        val hostActivity = activity
        if (hostActivity == null) {
            showMessage(openEvaluationErrorMessage)
            return
        }
        val missing =
            sdkPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            launchCustomEvaluation(hostActivity)
        } else {
            // Keep FINE/COARSE together even when approximate location is already granted.
            sdkPermissionLauncher.launch(sdkPermissions)
        }
    }

    fun retryEvaluation() {
        if (evaluationState.recoveryAction == QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION) {
            openEvaluationWithPermission()
        } else {
            sdkUiController.retryCurrentStep()
        }
    }

    fun requestLocationPermission() {
        val permissions =
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        val missing =
            permissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            viewModel.onLocationPermissionGranted()
        } else {
            locationPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.noticeMessage) {
        val message = uiState.errorMessage ?: uiState.noticeMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearTransientMessage()
        }
    }

    LaunchedEffect(uiState.submissionResult?.id, lifecycleState) {
        if (
            uiState.submissionResult != null &&
                currentPage == SalesPage.REGISTRATION_CONFIRM && navigator.canHandleCallback()
        ) {
            val result = requireNotNull(uiState.submissionResult)
            discardRegistrationPhotos()
            navigator.replaceTop(SalesRoute(SalesPage.SUBMIT_SUCCESS, result.id, result.pgUrl,
                address = registrationDraft.liveAddress, latitude = route?.latitude, longitude = route?.longitude))
        }
    }

    LaunchedEffect(uiState.evaluationCompleted, lifecycleState) {
        if (route?.page == SalesPage.DEVICE_STATUS && uiState.evaluationCompleted && navigator.canHandleCallback()) {
            sdkUiController.close()
            navigator.replaceTop(SalesRoute(SalesPage.EVALUATION_COMPLETE, route.customerId,
                recordId = uiState.evaluationRecordId))
        }
    }

    LaunchedEffect(evaluationState.stage, currentPage) {
        if (
            currentPage == SalesPage.DEVICE_STATUS &&
            evaluationState.stage.opensMeasurementPage()
        ) {
            measurementVisible = true
        } else if (
            currentPage == SalesPage.EVALUATION_GUIDE &&
            evaluationState.stage.opensDevicePage()
        ) {
            measurementVisible = false
        }
    }

    LaunchedEffect(uiState.sdkLaunchRequest, currentPage, lifecycleState) {
        val request = uiState.sdkLaunchRequest ?: return@LaunchedEffect
        if (!currentPage.ownsQlzEvaluationSession()) {
            viewModel.consumeSdkLaunchRequest(request)
            return@LaunchedEffect
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        val hostActivity = activity
        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
            viewModel.rejectSdkLaunchRequest(request)
        } else {
            if (!viewModel.consumeSdkLaunchRequest(request)) return@LaunchedEffect
            if (!sdkUiController.authorizeEvaluation(request.token)) {
                showMessage(openEvaluationErrorMessage)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        sdkUiController.onHostStarted()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        sdkUiController.onHostStopped()
    }
    DisposableEffect(sdkUiController) {
        onDispose {
            viewModel.cancelSdkAuthorization()
            sdkUiController.close()
        }
    }

    SalesPageBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentPage) {
                SalesPage.HOME -> {
                    AdaptiveAppNavigationScaffold(
                        modifier = Modifier.fillMaxSize(),
                        items = navigationItems,
                        selectedItemIndex = rootTab,
                        onItemSelected = { selected ->
                            when (selected) {
                                0 -> rootTab = 0
                                1 -> navigate(SalesPage.CUSTOMERS)
                                2 -> rootTab = 2
                            }
                        },
                    ) {
                        when (rootTab) {
                            2 ->
                                ProfileScreen(
                                    actions =
                                        ProfileActions(
                                            onNavigateToHaveServiceUserList =
                                                actions.onNavigateToHaveServiceUserList,
                                            onNavigateToNoServiceUserList =
                                                actions.onNavigateToNoServiceUserList,
                                            onOpenUserAgreement =
                                                actions.onOpenUserAgreement,
                                            onOpenPrivacyPolicy =
                                                actions.onOpenPrivacyPolicy,
                                        ),
                                    homeSharedViewModel = homeSharedViewModel,
                                )

                            else -> {
                                val loggedInUser = user
                                if (loggedInUser != null) {
                                    SalesDashboardScreen(
                                        user = loggedInUser,
                                        companyName = uiState.companyName,
                                        customers = uiState.recentCustomers,
                                        toDoCount = uiState.toDoCount,
                                        isToDoCountLoading =
                                            uiState.isToDoCountLoading,
                                        onRegisterCustomer = {
                                            navigate(SalesPage.REGISTRATION)
                                        },
                                        onReminders = {
                                            navigate(SalesPage.REMINDERS)
                                        },
                                        onCustomerClick = { customerId ->
                                            openCustomerDetail(
                                                customerId = customerId,
                                            )
                                        },
                                        modifier = Modifier,
                                    )
                                }
                            }
                        }
                    }
                }

                SalesPage.REMINDERS ->
                    SalesReminderListScreen(
                        reminders = uiState.toDoItems,
                        isLoading = uiState.isToDoListLoading,
                        errorMessage = uiState.toDoListErrorMessage,
                        onBack = ::back,
                        onRetry = viewModel::loadToDoList,
                        onReminderClick = { index ->
                            navigator.navigateWhenResumed(SalesRoute(SalesPage.REMINDER_DETAIL,
                                reminder = uiState.toDoItems.getOrNull(index)))
                        },
                    )

                SalesPage.REMINDER_DETAIL ->
                    SalesReminderDetailScreen(
                        reminder = route?.reminder,
                        onBack = ::back,
                    )

                SalesPage.CUSTOMERS ->
                    SalesCustomerListScreen(
                        customers = uiState.customers,
                        isLoading = uiState.isCustomerListLoading,
                        isLoadingMore = uiState.isCustomerListLoadingMore,
                        canLoadMore = uiState.canLoadMoreCustomers,
                        loadMoreErrorMessage = uiState.customerLoadMoreErrorMessage,
                        initialKeyword = uiState.customerSearchKeyword,
                        initialCheckState = uiState.customerCheckState,
                        hasLoadedCustomers = uiState.customerPageIndex > 0,
                        onBack = ::back,
                        onSearch = viewModel::searchCustomers,
                        onLoadMore = viewModel::loadNextCustomerPage,
                        onCustomerClick = { customerId ->
                            openCustomerDetail(
                                customerId = customerId,
                            )
                        },
                    )

                SalesPage.CUSTOMER_DETAIL ->
                    SalesCustomerDetailScreen(
                        customer = uiState.selectedCustomer,
                        isLoading = uiState.isCustomerDetailLoading,
                        errorMessage = uiState.customerDetailErrorMessage,
                        onBack = ::back,
                        onRetry = viewModel::retryCustomerDetail,
                        onEvaluate = { customerId ->
                            val customer = uiState.selectedCustomer
                            navigator.navigateWhenResumed(SalesRoute(SalesPage.EVALUATION_CHOICE, customerId,
                                pgUrl = customer?.pgUrl.orEmpty(), address = customer?.liveAddress.orEmpty(),
                                latitude = customer?.liveLat?.toDoubleOrNull(), longitude = customer?.liveLng?.toDoubleOrNull()))
                        },
                        onOpenReport = {
                            openLatestReport()
                        },
                    )

                SalesPage.REGISTRATION ->
                    SalesRegistrationScreen(
                        draft = registrationDraft,
                        photoUris = photoUris,
                        location = uiState.currentLocation,
                        onDraftChange = { registrationDraft = it },
                        onTakePhoto = ::requestSalesCamera,
                        onRemovePhoto = { removed ->
                            viewModel.discardManagedPhoto(removed)
                            photoUriStrings =
                                photoUriStrings.filterNot {
                                    it == removed.toString()
                                }
                        },
                        onRequestLocation = ::requestLocationPermission,
                        onBack = ::back,
                        onContinue = {
                            navigator.replaceTop(SalesRoute(SalesPage.REGISTRATION_CONFIRM,
                                draft = registrationDraft, photos = photoUriStrings,
                                latitude = uiState.currentLocation?.latitude, longitude = uiState.currentLocation?.longitude))
                        },
                        onValidationError = ::showMessage,
                    )

                SalesPage.REGISTRATION_CONFIRM ->
                    SalesInformationConfirmationScreen(
                        draft = registrationDraft,
                        photoUris = photoUris,
                        onBack = ::back,
                        onSubmit = {
                            viewModel.submitCustomer(
                                draft = registrationDraft,
                                photoUris = photoUris,
                                location = route?.latitude?.let { lat -> route.longitude?.let { lng ->
                                    LocationResult(lat, lng, "registration")
                                } },
                            )
                        },
                    )

                SalesPage.SUBMIT_SUCCESS ->
                    SalesSubmitSuccessScreen(
                        onBack = ::finishSubmissionFlow,
                        onEvaluation = {
                            navigator.navigateWhenResumed(requireNotNull(route).copy(page = SalesPage.EVALUATION_CHOICE))
                        },
                    )

                SalesPage.EVALUATION_CHOICE ->
                    SalesEvaluationChoiceScreen(
                        onBack = ::back,
                        onAutomaticEvaluation = {
                            startAutomaticEvaluation(route?.customerId ?: 0)
                        },
                        onFormEvaluation = {
                            val formUrl = route?.pgUrl.orEmpty()
                            openFormEvaluation(formUrl)
                        },
                    )

                SalesPage.DEVICE_STATUS ->
                    SalesDeviceStatusScreen(
                        evaluationState = evaluationState,
                        isPreparing = uiState.isSdkTokenLoading,
                        onBack = ::back,
                        onStartScan = ::openEvaluationWithPermission,
                        onSelectDevice = sdkUiController::selectDevice,
                        onRetry = ::retryEvaluation,
                        onRecheckEnvironment = ::openEvaluationWithPermission,
                    )

                SalesPage.EVALUATION_GUIDE ->
                    SalesEvaluationGuideScreen(
                        evaluationState = evaluationState,
                        onBack = ::back,
                        onRetry = {
                            if (evaluationState.recoveryAction.returnsToDeviceScan()) {
                                measurementVisible = false
                            }
                            retryEvaluation()
                        },
                    )

                SalesPage.EVALUATION_COMPLETE ->
                    SalesEvaluationCompleteScreen(
                        hasReport = !uiState.evaluationResult?.pgUrl.isNullOrBlank(),
                        grade = uiState.evaluationResult?.pgResult,
                        isLoading = uiState.isEvaluationResultLoading,
                        resultError = uiState.evaluationResultError,
                        onRefresh = viewModel::loadEvaluationResult,
                        onBack = ::back,
                        onDone = ::back,
                        onOpenReport = {
                            uiState.evaluationResult?.pgUrl?.takeIf { it.isNotBlank() }?.let {
                                actions.onOpenEvaluationReport(it, evaluationReportTitle)
                            }
                        },
                    )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
            )
            SalesLoadingOverlay(
                isVisible = uiState.isLoading || uiState.isSdkTokenLoading,
                message = if (uiState.isSdkTokenLoading) {
                    stringResource(R.string.sales_loading_prepare_evaluation)
                } else uiState.operation,
            )
        }
    }

    uiState.evaluationPrepareErrorMessage?.let { message ->
        SalesEvaluationPrepareErrorDialog(
            message = message,
            onExit = {
                viewModel.clearEvaluationPrepareError()
                back()
            },
        )
    }

    if (showCameraPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice(cameraPermissionPurpose),
            onConfirm = {
                showCameraPurposeNotice = false
                salesCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPurposeNotice = false },
        )
    }
}

internal val salesCustomerDraftSaver =
    listSaver<SalesCustomerDraft, String>(
        save = { draft ->
            listOf(
                draft.userName,
                draft.identityCardNumber,
                draft.guardianName,
                draft.guardianPhone,
                draft.guardianRelation,
                draft.liveAddress,
                draft.isDisability.toString(),
                draft.remarks,
            )
        },
        restore = { values ->
            SalesCustomerDraft(
                userName = values[0],
                identityCardNumber = values[1],
                guardianName = values[2],
                guardianPhone = values[3],
                guardianRelation = values[4],
                liveAddress = values[5],
                isDisability = values.getOrNull(6) == "true",
                remarks = values.getOrNull(7).orEmpty(),
            )
        },
    )

internal fun SalesUiState.toQlzEvaluationUploadContext(
    registrationAddress: String,
): QlzEvaluationUploadContext {
    val matchingCustomer =
        selectedCustomer?.takeIf { customer ->
            customer.id == selectedCustomerId
        }
    return QlzEvaluationUploadContext(
        latitude =
            matchingCustomer?.liveLat.orEmpty().ifBlank {
                currentLocation?.latitude?.toString().orEmpty()
            },
        longitude =
            matchingCustomer?.liveLng.orEmpty().ifBlank {
                currentLocation?.longitude?.toString().orEmpty()
            },
        address =
            matchingCustomer?.liveAddress.orEmpty().ifBlank {
                registrationAddress
            },
    ).normalized()
}

internal fun QlzEvaluationStage.opensMeasurementPage(): Boolean =
    this in
        setOf(
            QlzEvaluationStage.CONNECTING,
            QlzEvaluationStage.CONNECTED,
            QlzEvaluationStage.MEASURING,
            QlzEvaluationStage.POWER_PAUSED,
            QlzEvaluationStage.UPLOADING,
            QlzEvaluationStage.COMPLETED,
        )

internal fun SalesPage.ownsQlzEvaluationSession(): Boolean =
    this == SalesPage.DEVICE_STATUS || this == SalesPage.EVALUATION_GUIDE

internal fun QlzEvaluationStage.opensDevicePage(): Boolean =
    this in setOf(
        QlzEvaluationStage.IDLE,
        QlzEvaluationStage.AUTHORIZING,
        QlzEvaluationStage.READY_TO_SCAN,
        QlzEvaluationStage.SCANNING,
        QlzEvaluationStage.SCAN_RESULTS,
        QlzEvaluationStage.SCAN_EMPTY,
    )

internal fun QlzEvaluationRecoveryAction?.returnsToDeviceScan(): Boolean =
    this == QlzEvaluationRecoveryAction.RETRY_SCAN
