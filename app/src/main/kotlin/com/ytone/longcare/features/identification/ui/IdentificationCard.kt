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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ytone.longcare.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType

enum class IdentificationPersonType(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val avatarRes: Int,
    @param:StringRes val avatarDescriptionRes: Int,
    val verificationType: VerificationType,
) {
    SERVICE_PERSON(
        labelRes = R.string.identification_service_person,
        avatarRes = R.drawable.ic_service_person,
        avatarDescriptionRes = R.string.identification_service_person_avatar,
        verificationType = VerificationType.SERVICE_PERSON,
    ),
    ELDER(
        labelRes = R.string.identification_elder,
        avatarRes = R.drawable.ic_elder_person,
        avatarDescriptionRes = R.string.identification_elder_avatar,
        verificationType = VerificationType.ELDER,
    ),
}

@Composable
fun IdentificationCard(
    personType: IdentificationPersonType,
    isVerified: Boolean,
    onVerifyClick: () -> Unit,
    viewModel: IdentificationViewModel,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState = PhotoUploadState.Initial,
    faceSetupState: FaceSetupState = FaceSetupState.Initial,
) {
    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    val currentVerificationType by viewModel.currentVerificationType.collectAsStateWithLifecycle()

    val isCurrentlyVerifying = currentVerificationType == personType.verificationType

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
