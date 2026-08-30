package com.ytone.longcare.feature.login.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginFeatureActionsTest {

    @Test
    fun `validation actions map each entry exactly once`() {
        val events = mutableListOf<String>()
        val actions = LoginValidationEntryActions(
            onOpenCameraValidation = { events += "camera" },
            onOpenBackupFaceVerification = { events += "backup-face" },
            onOpenManualFaceCapture = { events += "manual-face" },
            onOpenFaceVerificationValidation = { events += "face-validation" },
            onOpenNfcValidation = { events += "nfc-validation" },
        )

        actions.onOpenCameraValidation()
        actions.onOpenBackupFaceVerification()
        actions.onOpenManualFaceCapture()
        actions.onOpenFaceVerificationValidation()
        actions.onOpenNfcValidation()

        assertEquals(
            listOf(
                "camera",
                "backup-face",
                "manual-face",
                "face-validation",
                "nfc-validation",
            ),
            events,
        )
    }

    @Test
    fun `default validation actions are safe no ops`() {
        val actions = LoginValidationEntryActions()

        actions.onOpenCameraValidation()
        actions.onOpenBackupFaceVerification()
        actions.onOpenManualFaceCapture()
        actions.onOpenFaceVerificationValidation()
        actions.onOpenNfcValidation()
    }

    @Test
    fun `agreement links preserve app supplied values`() {
        val links = LoginAgreementLinks(
            userAgreementUrl = "http://192.0.2.1:8080/user",
            privacyPolicyUrl = "https://privacy.example.test/policy",
        )

        assertEquals("http://192.0.2.1:8080/user", links.userAgreementUrl)
        assertEquals("https://privacy.example.test/policy", links.privacyPolicyUrl)
    }
}
