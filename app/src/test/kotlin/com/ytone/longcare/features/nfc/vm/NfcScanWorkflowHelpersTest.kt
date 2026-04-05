package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcScanWorkflowHelpersTest {

    @Test
    fun `handleTagScanned routes start order with normalized tag id`() = runTest {
        var started: Triple<String, String, String>? = null

        handleTagScanned(
            event = AppEvent.TagScanned("ABC123", ScanSource.EXTERNAL_RFID),
            currentState = NfcSignInUiState.Initial,
            signInMode = SignInMode.START_ORDER,
            endOderInfo = null,
            onLocationRequest = { LocationRequestResult.Coordinates("121.47", "31.23") },
            onLocationError = { error("unexpected location error: $it") },
            onStartOrder = { tagId, longitude, latitude ->
                started = Triple(tagId, longitude, latitude)
            },
            onEndOrder = { _, _, _, _ -> error("unexpected end order") },
        )

        assertEquals(Triple("ABC123", "121.47", "31.23"), started)
    }

    @Test
    fun `reduceReaderUiState keeps business and device state separate`() {
        assertTrue(
            reduceReaderUiState(
                currentMode = ScanMode.EXTERNAL_RFID,
                event = AppEvent.ReaderConnectionChanged(connected = false),
                currentReaderState = ReaderUiState.Ready,
            ) is ReaderUiState.Disconnected,
        )
    }
}
