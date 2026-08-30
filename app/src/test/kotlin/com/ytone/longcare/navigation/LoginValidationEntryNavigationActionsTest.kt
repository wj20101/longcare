package com.ytone.longcare.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginValidationEntryNavigationActionsTest {

    @Test
    fun `app adapter maps all five login validation entries exactly once`() {
        val events = mutableListOf<String>()
        val actions = createLoginValidationEntryActions(
            onOpenCameraValidation = { events += "camera-route" },
            onOpenBackupFaceVerification = { events += "backup-face-route" },
            onOpenManualFaceCapture = { events += "manual-face-route" },
            onOpenFaceVerificationValidation = { events += "face-activity" },
            onOpenNfcValidation = { events += "nfc-activity" },
        )

        actions.onOpenCameraValidation()
        actions.onOpenBackupFaceVerification()
        actions.onOpenManualFaceCapture()
        actions.onOpenFaceVerificationValidation()
        actions.onOpenNfcValidation()

        assertEquals(
            listOf(
                "camera-route",
                "backup-face-route",
                "manual-face-route",
                "face-activity",
                "nfc-activity",
            ),
            events,
        )
    }
}
