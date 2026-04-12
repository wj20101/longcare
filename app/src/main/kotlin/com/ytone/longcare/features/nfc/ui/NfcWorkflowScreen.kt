package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.theme.bgGradientBrush

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
    val scanMode by nfcViewModel.scanMode.collectAsStateWithLifecycle()
    val readerUiState by nfcViewModel.readerUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    var acknowledgedErrorState by remember { mutableStateOf<NfcSignInUiState.Error?>(null) }

    LaunchedEffect(uiState) {
        if (uiState !is NfcSignInUiState.Error) {
            acknowledgedErrorState = null
        }
    }

    val signInState = mapNfcSignInState(uiState)

    val onBack = buildNfcWorkflowBackAction(signInMode, signInState, actions)
    CustomBackHandler(customAction = onBack)

    val locationHandlers = rememberNfcWorkflowLocationHandlers(
        context = context,
        orderKey = orderKey,
        nfcViewModel = nfcViewModel,
        locationTrackingViewModel = locationTrackingViewModel
    )

    NfcWorkflowEffects(
        activity = activity,
        orderKey = orderKey,
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        uiState = uiState,
        nfcViewModel = nfcViewModel,
        locationTrackingViewModel = locationTrackingViewModel,
        onLocationRequest = { locationHandlers.getCurrentLocationCoordinates() }
    )

    val titleRes = resolveNfcWorkflowTitleRes(signInMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                NfcWorkflowTopBar(
                    titleRes = titleRes,
                    onBack = onBack
                )
            },
            bottomBar = {
                NfcWorkflowBottomBar(
                    signInState = signInState,
                    signInMode = signInMode,
                    scanMode = scanMode,
                    readerUiState = readerUiState,
                    onSuccessClick = singleClick {
                        handleNfcSuccessAction(
                            signInMode = signInMode,
                            orderKey = orderKey,
                            endOderInfo = endOderInfo,
                            uiState = uiState,
                            nfcViewModel = nfcViewModel,
                            locationTrackingViewModel = locationTrackingViewModel,
                            actions = actions,
                            startTrackingWithPermission = locationHandlers.startTrackingWithPermission
                        )
                    },
                    onRetryClick = {
                        nfcViewModel.resetState()
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            NfcWorkflowBodyContent(
                paddingValues = paddingValues,
                orderKey = orderKey,
                signInMode = signInMode,
                endOderInfo = endOderInfo,
                nfcViewModel = nfcViewModel,
                signInState = signInState,
                scanMode = scanMode,
                readerUiState = readerUiState,
            )
        }

        val shouldShowErrorDialog = uiState is NfcSignInUiState.Error && acknowledgedErrorState !== uiState

        NfcWorkflowDialogs(
            pendingNfcData = pendingNfcData,
            uiState = uiState,
            shouldShowErrorDialog = shouldShowErrorDialog,
            onConfirmLocationActivation = nfcViewModel::confirmLocationActivation,
            onCancelLocationActivation = nfcViewModel::cancelLocationActivation,
            onConfirmEndOrder = nfcViewModel::confirmEndOrder,
            onCancelEndOrder = nfcViewModel::cancelEndOrder,
            onDismissErrorDialog = {
                (uiState as? NfcSignInUiState.Error)?.let { dismissedError ->
                    acknowledgedErrorState = dismissedError
                }
            }
        )
    }
}
