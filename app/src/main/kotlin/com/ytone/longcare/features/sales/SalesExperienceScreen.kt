package com.ytone.longcare.features.sales

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.AppBottomNavigation
import com.ytone.longcare.features.home.ui.CustomBottomNavigationItem
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.features.profile.ui.ProfileScreen
import kotlinx.coroutines.launch

@Composable
internal fun SalesExperienceScreen(
    actions: HomeActions,
    homeSharedViewModel: HomeSharedViewModel,
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var currentPageName by rememberSaveable {
        mutableStateOf(SalesPage.HOME.name)
    }
    var rootTab by rememberSaveable { mutableIntStateOf(0) }
    var detailReturnPageName by rememberSaveable {
        mutableStateOf(SalesPage.HOME.name)
    }
    var evaluationReturnPageName by rememberSaveable {
        mutableStateOf(SalesPage.HOME.name)
    }
    var reminderCustomerId by rememberSaveable { mutableIntStateOf(0) }
    var registrationDraft by remember {
        mutableStateOf(SalesCustomerDraft())
    }
    var photoUriStrings by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }

    val currentPage =
        runCatching { SalesPage.valueOf(currentPageName) }
            .getOrDefault(SalesPage.HOME)
    val photoUris = photoUriStrings.map(Uri::parse)
    val bottomItems =
        remember {
            listOf(
                CustomBottomNavigationItem("首页"),
                CustomBottomNavigationItem("我的客户"),
                CustomBottomNavigationItem("我的"),
            )
        }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigate(page: SalesPage) {
        currentPageName = page.name
    }

    fun goHome() {
        currentPageName = SalesPage.HOME.name
        rootTab = 0
    }

    fun finishSubmissionFlow() {
        // Leave the result page before clearing its state so a state emission
        // cannot re-enter or visually pin the completed submission route.
        goHome()
        registrationDraft = SalesCustomerDraft()
        photoUriStrings = emptyList()
        viewModel.resetSubmission()
    }

    fun openCustomerDetail(
        customerId: Int,
        returnPage: SalesPage,
    ) {
        detailReturnPageName = returnPage.name
        viewModel.loadCustomerDetail(customerId)
        navigate(SalesPage.CUSTOMER_DETAIL)
    }

    fun startAutomaticEvaluation(
        customerId: Int,
        returnPage: SalesPage,
    ) {
        if (customerId <= 0) {
            showMessage("请先选择需要评估的客户")
            return
        }
        evaluationReturnPageName = returnPage.name
        viewModel.prepareEvaluation(customerId)
        navigate(SalesPage.DEVICE_STATUS)
    }

    fun back() {
        when (currentPage) {
            SalesPage.HOME -> {
                if (rootTab != 0) rootTab = 0
            }

            SalesPage.REMINDERS -> goHome()
            SalesPage.REMINDER_DETAIL -> navigate(SalesPage.REMINDERS)
            SalesPage.CUSTOMERS -> goHome()
            SalesPage.CUSTOMER_DETAIL ->
                navigate(
                    runCatching { SalesPage.valueOf(detailReturnPageName) }
                        .getOrDefault(SalesPage.HOME)
                )

            SalesPage.REGISTRATION -> goHome()
            SalesPage.REGISTRATION_CONFIRM -> navigate(SalesPage.REGISTRATION)
            SalesPage.SUBMIT_SUCCESS -> finishSubmissionFlow()
            SalesPage.EVALUATION_CHOICE ->
                navigate(
                    runCatching { SalesPage.valueOf(evaluationReturnPageName) }
                        .getOrDefault(SalesPage.HOME)
                )

            SalesPage.DEVICE_STATUS ->
                navigate(
                    runCatching { SalesPage.valueOf(evaluationReturnPageName) }
                        .getOrDefault(SalesPage.HOME)
                )

            SalesPage.EVALUATION_GUIDE -> navigate(SalesPage.DEVICE_STATUS)
            SalesPage.EVALUATION_COMPLETE -> goHome()
        }
    }

    BackHandler(
        enabled = currentPage != SalesPage.HOME || rootTab != 0,
        onBack = ::back,
    )

    val sdkPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted =
                viewModel.requiredSdkPermissions().all { permission ->
                    permissions[permission] == true ||
                        ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
                }
            if (allGranted && activity != null) {
                viewModel.launchSdk(activity)
            } else {
                showMessage("请允许附近设备/定位权限后连接评估设备")
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
                showMessage("请允许定位权限后获取客户位置")
            }
        }

    fun openSdkWithPermission() {
        val hostActivity = activity
        if (hostActivity == null) {
            showMessage("当前无法打开评估页面，请稍后重试")
            return
        }
        navigate(SalesPage.EVALUATION_GUIDE)
        val missing =
            viewModel.requiredSdkPermissions().filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            viewModel.launchSdk(hostActivity)
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
            evaluationReturnPageName = SalesPage.SUBMIT_SUCCESS.name
            navigate(SalesPage.SUBMIT_SUCCESS)
        }
    }

    LaunchedEffect(uiState.evaluationCompleted) {
        if (uiState.evaluationCompleted != null) {
            navigate(SalesPage.EVALUATION_COMPLETE)
        }
    }

    SalesPageBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentPage) {
                SalesPage.HOME -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = {
                            AppBottomNavigation(
                                items = bottomItems,
                                selectedItemIndex = rootTab,
                                onItemSelected = { selected ->
                                    when (selected) {
                                        0 -> rootTab = 0
                                        1 -> navigate(SalesPage.CUSTOMERS)
                                        2 -> rootTab = 2
                                    }
                                },
                            )
                        },
                    ) { paddingValues ->
                        when (rootTab) {
                            2 ->
                                Box(
                                    modifier =
                                        Modifier.padding(
                                            bottom =
                                                paddingValues
                                                    .calculateBottomPadding()
                                        )
                                ) {
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
                                }

                            else -> {
                                val loggedInUser = user
                                if (loggedInUser != null) {
                                    SalesDashboardScreen(
                                        user = loggedInUser,
                                        companyName = uiState.companyName,
                                        customers = uiState.recentCustomers,
                                        onRegisterCustomer = {
                                            registrationDraft = SalesCustomerDraft()
                                            photoUriStrings = emptyList()
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
                                        modifier =
                                            Modifier.padding(
                                                bottom =
                                                    paddingValues
                                                        .calculateBottomPadding()
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                SalesPage.REMINDERS ->
                    SalesReminderListScreen(
                        customers = uiState.recentCustomers,
                        onBack = ::back,
                        onReminderClick = { customerId ->
                            reminderCustomerId = customerId
                            navigate(SalesPage.REMINDER_DETAIL)
                        },
                    )

                SalesPage.REMINDER_DETAIL ->
                    SalesReminderDetailScreen(
                        customer =
                            uiState.recentCustomers.firstOrNull {
                                it.id == reminderCustomerId
                            },
                        onBack = ::back,
                        onOpenCustomer = { customerId ->
                            openCustomerDetail(
                                customerId = customerId,
                                returnPage = SalesPage.REMINDERS,
                            )
                        },
                    )

                SalesPage.CUSTOMERS ->
                    SalesCustomerListScreen(
                        customers = uiState.customers,
                        isLoading = uiState.isCustomerListLoading,
                        initialKeyword = uiState.customerSearchKeyword,
                        initialCheckState = uiState.customerCheckState,
                        onBack = ::back,
                        onSearch = viewModel::searchCustomers,
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
                        onBack = ::back,
                        onEvaluate = { customerId ->
                            evaluationReturnPageName =
                                SalesPage.CUSTOMER_DETAIL.name
                            viewModel.selectCustomer(customerId)
                            navigate(SalesPage.EVALUATION_CHOICE)
                        },
                        onOpenReport = {
                            activity?.let(viewModel::openLatestReport)
                        },
                    )

                SalesPage.REGISTRATION ->
                    SalesRegistrationScreen(
                        draft = registrationDraft,
                        photoUris = photoUris,
                        location = uiState.currentLocation,
                        onDraftChange = { registrationDraft = it },
                        onPhotosSelected = { selected ->
                            photoUriStrings = selected.map(Uri::toString)
                        },
                        onRemovePhoto = { removed ->
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
                        onBack = ::finishSubmissionFlow,
                        onEvaluation = {
                            evaluationReturnPageName =
                                SalesPage.SUBMIT_SUCCESS.name
                            navigate(SalesPage.EVALUATION_CHOICE)
                        },
                    )

                SalesPage.EVALUATION_CHOICE ->
                    SalesEvaluationChoiceScreen(
                        onBack = ::back,
                        onAutomaticEvaluation = {
                            startAutomaticEvaluation(
                                customerId = uiState.selectedCustomerId,
                                returnPage = SalesPage.EVALUATION_CHOICE,
                            )
                        },
                        onFormEvaluation = {
                            val formUrl =
                                uiState.submissionResult?.pgUrl
                                    .orEmpty()
                                    .ifBlank {
                                        uiState.selectedCustomer?.pgUrl.orEmpty()
                                    }
                            if (activity != null) {
                                viewModel.openReportUrl(activity, formUrl)
                            }
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
                            uiState.evaluationCompleted?.reportUrl?.isNotBlank() == true,
                        onBack = ::goHome,
                        onDone = ::goHome,
                        onOpenReport = {
                            activity?.let(viewModel::openLatestReport)
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
}

private enum class SalesPage {
    HOME,
    REMINDERS,
    REMINDER_DETAIL,
    CUSTOMERS,
    CUSTOMER_DETAIL,
    REGISTRATION,
    REGISTRATION_CONFIRM,
    SUBMIT_SUCCESS,
    EVALUATION_CHOICE,
    DEVICE_STATUS,
    EVALUATION_GUIDE,
    EVALUATION_COMPLETE,
}
