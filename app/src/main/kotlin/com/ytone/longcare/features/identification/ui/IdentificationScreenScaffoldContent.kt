package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.theme.bgGradientBrush

@Composable
internal fun IdentificationScreenScaffoldContent(
    identificationState: IdentificationState,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState,
    faceSetupState: FaceSetupState,
    identificationViewModel: IdentificationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSelectService: () -> Unit,
    onVerifyServicePerson: () -> Unit,
    onVerifyElder: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                IdentificationTopBar(onNavigateBack = onNavigateBack)
            },
            bottomBar = {
                IdentificationBottomBar(
                    identificationState = identificationState,
                    onNavigateToSelectService = onNavigateToSelectService
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            IdentificationBodyContent(
                identificationState = identificationState,
                faceVerificationState = faceVerificationState,
                photoUploadState = photoUploadState,
                faceSetupState = faceSetupState,
                identificationViewModel = identificationViewModel,
                onVerifyServicePerson = onVerifyServicePerson,
                onVerifyElder = onVerifyElder,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
