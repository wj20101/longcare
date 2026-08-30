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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.platform.sales.rememberSalesSdkUiController
import com.ytone.longcare.presentation.sales.SalesNavigationState
import com.ytone.longcare.presentation.sales.SalesBackEffect
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.presentation.sales.reduceSalesBack
import com.ytone.longcare.presentation.sales.rememberSalesNavigationState
import kotlinx.coroutines.launch

@Composable
internal fun SalesExperienceScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel,
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    val capturedImageUri by actions.capturedImageUriFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val sdkUiController = rememberSalesSdkUiController()
    val sdkPermissions = remember(sdkUiController) {
        sdkUiController.requiredRuntimePermissions()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val navigationState = rememberSalesNavigationState()
    var registrationDraft by rememberSaveable(stateSaver = salesCustomerDraftSaver) {
        mutableStateOf(SalesCustomerDraft())
    }
    var photoUriStrings by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var showCameraPurposeNotice by rememberSaveable { mutableStateOf(false) }

    val currentPage = navigationState.currentPage
    val photoUris = photoUriStrings.map(String::toUri)
    val selectCustomerMessage = stringResource(R.string.sales_error_select_customer)
    val evaluationPermissionMessage =
        stringResource(R.string.sales_error_evaluation_permission)
    val locationPermissionMessage =
        stringResource(R.string.sales_error_location_permission)
    val openEvaluationErrorMessage =
        stringResource(R.string.sales_error_open_evaluation)
    val evaluationNotReadyMessage =
        stringResource(R.string.sales_error_evaluation_not_ready)
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
        navigationState.navigate(page)
    }

    fun launchEvaluationSdk(hostActivity: android.app.Activity) {
        val token = uiState.checkToken?.token.orEmpty()
        if (token.isBlank()) {
            showMessage(evaluationNotReadyMessage)
            return
        }
        sdkUiController.openEvaluation(
            activity = hostActivity,
            token = token,
            onEvent = viewModel::onSdkEvent,
        )
    }

    fun openFormEvaluation(formUrl: String) {
        val normalizedFormUrl = formUrl.trim()
        if (normalizedFormUrl.isBlank()) {
            showMessage(reportUrlEmptyMessage)
        } else {
            actions.onOpenWebPage(normalizedFormUrl, evaluationFormTitle)
        }
    }

    fun openLatestReport() {
        val reportUrl = uiState.selectedCustomer.serverAssessmentReportUrl()
        if (reportUrl.isBlank()) {
            showMessage(noReportMessage)
        } else {
            actions.onOpenWebPage(reportUrl, evaluationReportTitle)
        }
    }

    fun goHome() {
        navigationState.goHome()
    }

    fun discardRegistrationPhotos() {
        if (photoUriStrings.isNotEmpty()) {
            viewModel.discardManagedPhotos(photoUriStrings.map(String::toUri))
            photoUriStrings = emptyList()
        }
    }

    fun clearRegistrationFlow() {
        registrationDraft = SalesCustomerDraft()
        discardRegistrationPhotos()
        viewModel.resetSubmission()
    }

    fun openCustomerDetail(
        customerId: Int,
        returnPage: SalesPage,
    ) {
        viewModel.loadCustomerDetail(customerId)
        navigationState.showCustomerDetail(returnPage)
    }

    fun startAutomaticEvaluation(customerId: Int) {
        if (customerId <= 0) {
            showMessage(selectCustomerMessage)
            return
        }
        viewModel.prepareEvaluation(customerId)
        navigate(SalesPage.DEVICE_STATUS)
    }

    fun back() {
        val result = reduceSalesBack(navigationState.snapshot())
        // Apply navigation first so submission state emissions cannot visually
        // re-enter a page that the user has already left.
        navigationState.apply(result.snapshot)
        when (result.effect) {
            SalesBackEffect.None -> Unit
            SalesBackEffect.ClearRegistration -> clearRegistrationFlow()
        }
    }

    BackHandler(
        enabled = navigationState.canHandleBack,
        onBack = ::back,
    )

    LaunchedEffect(currentPage, navigationState.rootTab) {
        when {
            currentPage == SalesPage.HOME && navigationState.rootTab == 0 ->
                viewModel.loadToDoCount()

            currentPage == SalesPage.REMINDERS ->
                viewModel.loadToDoList()

            currentPage == SalesPage.REMINDER_DETAIL &&
                uiState.toDoItems.isEmpty() ->
                viewModel.loadToDoList()
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
                launchEvaluationSdk(activity)
            } else {
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

    fun openSdkWithPermission() {
        val hostActivity = activity
        if (hostActivity == null) {
            showMessage(openEvaluationErrorMessage)
            return
        }
        navigate(SalesPage.EVALUATION_GUIDE)
        val missing =
            sdkPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            launchEvaluationSdk(hostActivity)
        } else {
            sdkPermissionLauncher.launch(missing.toTypedArray())
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

    LaunchedEffect(uiState.submissionResult?.id) {
        if (
            uiState.submissionResult != null &&
                currentPage == SalesPage.REGISTRATION_CONFIRM
        ) {
            navigationState.rememberEvaluationChoiceReturnPage(SalesPage.SUBMIT_SUCCESS)
            navigate(SalesPage.SUBMIT_SUCCESS)
        }
    }

    LaunchedEffect(uiState.evaluationCompleted) {
        if (uiState.evaluationCompleted != null) {
            navigate(SalesPage.EVALUATION_COMPLETE)
        }
    }

    LaunchedEffect(uiState.sdkLaunchRequest?.id) {
        val request = uiState.sdkLaunchRequest ?: return@LaunchedEffect
        val hostActivity = activity
        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
            viewModel.rejectSdkLaunchRequest(request.id)
        } else {
            viewModel.consumeSdkLaunchRequest(request.id)
            sdkUiController.openEvaluation(
                activity = hostActivity,
                token = request.token,
                onEvent = viewModel::onSdkEvent,
            )
        }
    }

    SalesPageBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentPage) {
                SalesPage.HOME -> {
                    AdaptiveAppNavigationScaffold(
                        modifier = Modifier.fillMaxSize(),
                        items = navigationItems,
                        selectedItemIndex = navigationState.rootTab,
                        onItemSelected = { selected ->
                            when (selected) {
                                0 -> navigationState.selectRootTab(0)
                                1 -> navigate(SalesPage.CUSTOMERS)
                                2 -> navigationState.selectRootTab(2)
                            }
                        },
                    ) {
                        when (navigationState.rootTab) {
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
                                            discardRegistrationPhotos()
                                            registrationDraft = SalesCustomerDraft()
                                            viewModel.resetSubmission()
                                            navigate(SalesPage.REGISTRATION)
                                        },
                                        onReminders = {
                                            navigate(SalesPage.REMINDERS)
                                        },
                                        onCustomerClick = { customerId ->
                                            openCustomerDetail(
                                                customerId = customerId,
                                                returnPage = SalesPage.HOME,
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
                            navigationState.selectReminder(index)
                        },
                    )

                SalesPage.REMINDER_DETAIL ->
                    SalesReminderDetailScreen(
                        reminder = uiState.toDoItems.getOrNull(navigationState.reminderIndex),
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
                        onBack = ::back,
                        onSearch = viewModel::searchCustomers,
                        onLoadMore = viewModel::loadNextCustomerPage,
                        onCustomerClick = { customerId ->
                            openCustomerDetail(
                                customerId = customerId,
                                returnPage = SalesPage.CUSTOMERS,
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
                            navigationState.rememberEvaluationChoiceReturnPage(
                                SalesPage.CUSTOMER_DETAIL
                            )
                            viewModel.selectCustomer(customerId)
                            navigate(SalesPage.EVALUATION_CHOICE)
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
                            navigate(SalesPage.REGISTRATION_CONFIRM)
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
                            )
                        },
                    )

                SalesPage.SUBMIT_SUCCESS ->
                    SalesSubmitSuccessScreen(
                        onBack = ::back,
                        onEvaluation = {
                            navigationState.rememberEvaluationChoiceReturnPage(
                                SalesPage.SUBMIT_SUCCESS
                            )
                            navigate(SalesPage.EVALUATION_CHOICE)
                        },
                    )

                SalesPage.EVALUATION_CHOICE ->
                    SalesEvaluationChoiceScreen(
                        onBack = ::back,
                        onAutomaticEvaluation = {
                            startAutomaticEvaluation(uiState.selectedCustomerId)
                        },
                        onFormEvaluation = {
                            val formUrl =
                                uiState.submissionResult?.pgUrl
                                    .orEmpty()
                                    .ifBlank {
                                        uiState.selectedCustomer?.pgUrl.orEmpty()
                                    }
                            openFormEvaluation(formUrl)
                        },
                    )

                SalesPage.DEVICE_STATUS ->
                    SalesDeviceStatusScreen(
                        connectedDeviceName = uiState.connectedDeviceName,
                        tokenReady = uiState.checkToken?.token?.isNotBlank() == true,
                        progressText = uiState.sdkProgressText,
                        onBack = ::back,
                        onStartEvaluation = ::openSdkWithPermission,
                    )

                SalesPage.EVALUATION_GUIDE ->
                    SalesEvaluationGuideScreen(
                        connectedDeviceName = uiState.connectedDeviceName,
                        progressText = uiState.sdkProgressText,
                        onBack = ::back,
                        onOpenSdk = ::openSdkWithPermission,
                    )

                SalesPage.EVALUATION_COMPLETE ->
                    SalesEvaluationCompleteScreen(
                        hasReport =
                            uiState.selectedCustomer.serverAssessmentReportUrl().isNotBlank(),
                        onBack = ::back,
                        onDone = ::goHome,
                        onOpenReport = {
                            openLatestReport()
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
                isVisible = uiState.isLoading,
                message = uiState.operation,
            )
        }
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

private val salesCustomerDraftSaver =
    listSaver<SalesCustomerDraft, String>(
        save = { draft ->
            listOf(
                draft.userName,
                draft.identityCardNumber,
                draft.guardianName,
                draft.guardianPhone,
                draft.guardianRelation,
                draft.liveAddress,
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
            )
        },
    )
