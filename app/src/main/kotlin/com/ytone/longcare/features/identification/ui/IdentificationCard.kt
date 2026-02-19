package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType

internal object IdentificationConstants {
    const val SERVICE_PERSON = "服务人员"
    const val ELDER = "老人"
}

@Composable
fun IdentificationCard(
    personType: String,
    isVerified: Boolean,
    onVerifyClick: () -> Unit,
    viewModel: IdentificationViewModel,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState = PhotoUploadState.Initial,
    faceSetupState: FaceSetupState = FaceSetupState.Initial
) {
    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    val currentVerificationType by viewModel.currentVerificationType.collectAsStateWithLifecycle()

    val isCurrentlyVerifying = when (personType) {
        IdentificationConstants.SERVICE_PERSON -> currentVerificationType == VerificationType.SERVICE_PERSON
        IdentificationConstants.ELDER -> currentVerificationType == VerificationType.ELDER
        else -> false
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IdentificationAvatar(personType = personType)
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                IdentificationCardStatusArea(
                    personType = personType,
                    isVerified = isVerified,
                    isCurrentlyVerifying = isCurrentlyVerifying,
                    identificationState = identificationState,
                    faceVerificationState = faceVerificationState,
                    photoUploadState = photoUploadState,
                    faceSetupState = faceSetupState,
                    onVerifyClick = onVerifyClick,
                    onRetryFaceSetup = {
                        viewModel.resetFaceSetupState()
                        onVerifyClick()
                    },
                    onRetryFaceVerification = {
                        viewModel.resetFaceVerificationState()
                        onVerifyClick()
                    }
                )
            }
        }
    }
}
