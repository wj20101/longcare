package com.ytone.longcare.navigation

import android.content.Intent
import androidx.navigation.NavController
import com.ytone.longcare.feature.login.R as LoginR
import com.ytone.longcare.feature.login.api.LoginValidationEntryActions
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.presentation.validation.FaceVerificationValidationActivity
import com.ytone.longcare.presentation.validation.NfcValidationActivity

internal fun NavController.createLoginValidationEntryActions(): LoginValidationEntryActions =
    createLoginValidationEntryActions(
        onOpenCameraValidation = {
            navigateToCamera(
                WatermarkData(
                    title = context.getString(LoginR.string.login_validation_entry_camera),
                    insuredPerson = "",
                    caregiver = "",
                    address = "",
                ),
            )
        },
        onOpenBackupFaceVerification = { navigateToFaceVerificationWithAutoSign() },
        onOpenManualFaceCapture = { navigateToManualFaceCapture() },
        onOpenFaceVerificationValidation = {
            context.startActivity(
                Intent(context, FaceVerificationValidationActivity::class.java),
            )
        },
        onOpenNfcValidation = {
            context.startActivity(Intent(context, NfcValidationActivity::class.java))
        },
    )

internal fun createLoginValidationEntryActions(
    onOpenCameraValidation: () -> Unit,
    onOpenBackupFaceVerification: () -> Unit,
    onOpenManualFaceCapture: () -> Unit,
    onOpenFaceVerificationValidation: () -> Unit,
    onOpenNfcValidation: () -> Unit,
): LoginValidationEntryActions = LoginValidationEntryActions(
    onOpenCameraValidation = onOpenCameraValidation,
    onOpenBackupFaceVerification = onOpenBackupFaceVerification,
    onOpenManualFaceCapture = onOpenManualFaceCapture,
    onOpenFaceVerificationValidation = onOpenFaceVerificationValidation,
    onOpenNfcValidation = onOpenNfcValidation,
)
