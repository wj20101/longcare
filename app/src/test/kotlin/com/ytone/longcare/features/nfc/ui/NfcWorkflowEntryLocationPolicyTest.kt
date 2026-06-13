package com.ytone.longcare.features.nfc.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcWorkflowEntryLocationPolicyTest {

    @Test
    fun `nfc workflow prepares location on page entry`() {
        val screenSource = File(
            "src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt"
        ).readText()
        val effectsSource = File(
            "src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt"
        ).readText()
        val handlersSource = File(
            "src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt"
        ).readText()

        assertTrue(screenSource.contains("onEntryLocationPrepare = locationHandlers.prepareLocationOnEntry"))
        assertTrue(effectsSource.contains("LaunchedEffect(orderKey, signInMode)"))
        assertTrue(effectsSource.contains("onEntryLocationPrepare()"))
        assertTrue(handlersSource.contains("val prepareLocationOnEntry: () -> Unit"))
        assertTrue(handlersSource.contains("showLocationOnlyPurposeNotice = true"))
        assertTrue(handlersSource.contains("isLocationPreparing = true"))
        assertTrue(handlersSource.contains("isLocationPreparing = false"))
    }
}
