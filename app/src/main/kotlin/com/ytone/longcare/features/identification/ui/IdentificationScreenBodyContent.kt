package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState

@Composable
internal fun IdentificationBodyContent(
    identificationState: IdentificationState,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState,
    faceSetupState: FaceSetupState,
    identificationViewModel: IdentificationViewModel,
    onVerifyServicePerson: () -> Unit,
    onVerifyElder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "请按照要求进行人脸识别",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        IdentificationCard(
            personType = IdentificationConstants.SERVICE_PERSON,
            isVerified = identificationState.ordinal >= IdentificationState.SERVICE_VERIFIED.ordinal,
            onVerifyClick = onVerifyServicePerson,
            viewModel = identificationViewModel,
            faceVerificationState = faceVerificationState,
            faceSetupState = faceSetupState
        )

        Spacer(modifier = Modifier.height(16.dp))

        IdentificationCard(
            personType = IdentificationConstants.ELDER,
            isVerified = identificationState.ordinal >= IdentificationState.ELDER_VERIFIED.ordinal,
            onVerifyClick = onVerifyElder,
            viewModel = identificationViewModel,
            faceVerificationState = faceVerificationState,
            photoUploadState = photoUploadState
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
