package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper.openLocationSettings
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.theme.bgGradientBrush
import kotlinx.coroutines.CancellationException

enum class SignInState {
    IDLE,
    SUCCESS,
    FAILURE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcWorkflowScreen(
    actions: NfcWorkflowActions,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo? = null,
    nfcViewModel: NfcWorkflowViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel()
) {
    val uiState by nfcViewModel.uiState.collectAsStateWithLifecycle()
    val pendingNfcData by nfcViewModel.pendingNfcData.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    val signInState = when (uiState) {
        is NfcSignInUiState.Loading -> SignInState.IDLE
        is NfcSignInUiState.Success -> SignInState.SUCCESS
        is NfcSignInUiState.Error -> SignInState.FAILURE
        is NfcSignInUiState.Initial -> SignInState.IDLE
        is NfcSignInUiState.ShowConfirmDialog -> SignInState.IDLE
    }

    val onBack: () -> Unit = {
        if (signInMode == SignInMode.END_ORDER && signInState == SignInState.SUCCESS) {
            actions.onNavigateHomeAndClearStack()
        } else {
            actions.onNavigateBack()
        }
    }
    CustomBackHandler(customAction = onBack)

    val trackingPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
    )

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { }
    )

    fun checkLocationPermissionAndStartTracking() {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(
            context = context,
            permissionLauncher = trackingPermissionLauncher,
            onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
        )
    }

    fun requestLocationPermissionOnly() {
        if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
            locationOnlyPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
        }
    }

    suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                requestLocationPermissionOnly()
                return Pair("", "")
            }

            if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                openLocationSettings(context)
                nfcViewModel.showError("请开启定位服务以获取位置信息")
                return Pair("", "")
            }

            nfcViewModel.getCurrentLocationCoordinates()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair("", "")
        }
    }

    NfcWorkflowEffects(
        activity = activity,
        orderKey = orderKey,
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        uiState = uiState,
        nfcViewModel = nfcViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        onLocationRequest = { getCurrentLocationCoordinates() }
    )

    val titleRes = when (signInMode) {
        SignInMode.START_ORDER -> R.string.nfc_sign_in_title
        SignInMode.END_ORDER -> R.string.nfc_sign_out_title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(titleRes),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = singleClick { onBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                NfcWorkflowBottomBar(
                    signInState = signInState,
                    signInMode = signInMode,
                    onSuccessClick = singleClick {
                        when (signInMode) {
                            SignInMode.START_ORDER -> {
                                checkLocationPermissionAndStartTracking()
                                actions.onNavigateToIdentification(orderKey)
                            }

                            SignInMode.END_ORDER -> {
                                locationTrackingViewModel.onStopClicked()
                                val successState = uiState as? NfcSignInUiState.Success
                                val trueServiceTime = successState?.endOrderSuccessData?.trueServiceTime ?: 0
                                val serviceCompleteData = nfcViewModel.buildServiceCompleteDataFromCache(
                                    orderKey = orderKey,
                                    endOderInfo = endOderInfo,
                                    trueServiceTime = trueServiceTime
                                )
                                actions.onNavigateToServiceComplete(orderKey, serviceCompleteData)
                            }
                        }
                    },
                    onRetryClick = {
                        nfcViewModel.resetState()
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.nfc_sign_in_prompt),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                SignInContentCard(signInState = signInState)

                Spacer(modifier = Modifier.height(24.dp))

                if (BuildConfig.USE_MOCK_DATA) {
                    Button(
                        onClick = {
                            nfcViewModel.mockNfcScan(
                                orderKey = orderKey,
                                signInMode = signInMode,
                                endOderInfo = endOderInfo
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mock NFC Scan (Debug Only)")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        NfcWorkflowDialogs(
            pendingNfcData = pendingNfcData,
            uiState = uiState,
            onConfirmLocationActivation = nfcViewModel::confirmLocationActivation,
            onCancelLocationActivation = nfcViewModel::cancelLocationActivation,
            onConfirmEndOrder = nfcViewModel::confirmEndOrder,
            onCancelEndOrder = nfcViewModel::cancelEndOrder
        )
    }
}
