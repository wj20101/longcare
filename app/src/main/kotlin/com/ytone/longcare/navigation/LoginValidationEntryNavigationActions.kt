package com.ytone.longcare.navigation

import androidx.navigation.NavController
import com.ytone.longcare.feature.login.api.LoginValidationEntryActions
import com.ytone.longcare.model.WatermarkData

internal fun NavController.createLoginValidationEntryActions(): LoginValidationEntryActions =
    LoginValidationEntryActions(
        onOpenCameraValidation = {
            navigateToCamera(
                WatermarkData(
                    title = "拍照验证",
                    insuredPerson = "",
                    caregiver = "",
                    address = "",
                ),
            )
        },
        onOpenBackupFaceVerification = { navigateToFaceVerificationWithAutoSign() },
        onOpenManualFaceCapture = { navigateToManualFaceCapture() },
    )
