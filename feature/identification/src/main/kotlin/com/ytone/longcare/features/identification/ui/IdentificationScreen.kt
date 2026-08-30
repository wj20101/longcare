package com.ytone.longcare.features.identification.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLauncher
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import kotlinx.coroutines.launch

@Composable
internal fun IdentificationRouteScreen(
    actions: IdentificationActions,
    orderKey: OrderKey,
    faceSdkLauncher: IdentificationFaceSdkLauncher,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    identificationViewModel: IdentificationViewModel,
) {
    val context = LocalContext.current
    val cameraPermissionRequired = stringResource(R.string.identification_camera_permission_required)
    val screenUiState by identificationViewModel.screenUiState.collectAsStateWithLifecycle()
    val capturedImageUri by actions.capturedImageUriFlow.collectAsStateWithLifecycle()
    val faceImagePath by actions.faceImagePathFlow.collectAsStateWithLifecycle()
    val defaultFaceVerificationResult by
        actions.defaultFaceVerificationResultFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCameraPurposeNotice by remember { mutableStateOf(false) }

    CustomBackHandler(customAction = actions.onNavigateBack)

    fun openElderCamera() {
        scope.launch {
            val watermarkData = identificationViewModel.generateWatermarkData(
                address = sharedOrderDetailViewModel.getUserAddress(orderKey),
                orderKey = orderKey,
            )
            actions.onNavigateToCamera(watermarkData)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                openElderCamera()
            } else {
                Toast.makeText(context, cameraPermissionRequired, Toast.LENGTH_SHORT).show()
            }
        },
    )

    fun requestElderRecordPhoto() {
        if (UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
            openElderCamera()
        } else {
            showCameraPurposeNotice = true
        }
    }

    IdentificationScreenEffects(
        actions = actions,
        orderKey = orderKey,
        sharedOrderDetailViewModel = sharedOrderDetailViewModel,
        identificationViewModel = identificationViewModel,
        faceSdkLauncher = faceSdkLauncher,
        screenUiState = screenUiState,
        capturedImageUri = capturedImageUri,
        faceImagePath = faceImagePath,
        defaultFaceVerificationResult = defaultFaceVerificationResult,
        context = context,
    )

    val renderState = remember(screenUiState) { screenUiState.toRenderState() }
    IdentificationScreenContent(
        state = renderState,
        onEvent = { event ->
            when (event) {
                IdentificationScreenEvent.NavigateBack -> actions.onNavigateBack()
                IdentificationScreenEvent.ContinueToServiceSelection ->
                    actions.onNavigateToSelectService(orderKey)

                IdentificationScreenEvent.VerifyServicePerson ->
                    identificationViewModel.verifyServicePerson(orderKey)

                IdentificationScreenEvent.CaptureElderPhoto -> requestElderRecordPhoto()
                IdentificationScreenEvent.RetryFaceSetup -> {
                    identificationViewModel.resetFaceSetupState()
                    identificationViewModel.verifyServicePerson(orderKey)
                }

                is IdentificationScreenEvent.RetryFaceVerification -> {
                    identificationViewModel.resetFaceVerificationState()
                    when (event.personType) {
                        IdentificationPersonType.SERVICE_PERSON ->
                            identificationViewModel.verifyServicePerson(orderKey)

                        IdentificationPersonType.ELDER -> requestElderRecordPhoto()
                    }
                }
            }
        },
    )

    if (showCameraPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice(
                stringResource(R.string.identification_elder_photo_purpose),
            ),
            onConfirm = {
                showCameraPurposeNotice = false
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPurposeNotice = false },
        )
    }
}
