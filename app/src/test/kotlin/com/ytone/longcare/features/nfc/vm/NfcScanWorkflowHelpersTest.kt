package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `handleTagScanned ignores blank tag id`() = runTest {
        var locationRequested = false
        var started = false
        var ended = false

        handleTagScanned(
            event = AppEvent.TagScanned("   ", ScanSource.EXTERNAL_RFID),
            currentState = NfcSignInUiState.Initial,
            signInMode = SignInMode.START_ORDER,
            endOderInfo = null,
            onLocationRequest = {
                locationRequested = true
                LocationRequestResult.Coordinates("121.47", "31.23")
            },
            onLocationError = { error("unexpected location error: $it") },
            onStartOrder = { _, _, _ -> started = true },
            onEndOrder = { _, _, _, _ -> ended = true },
        )

        assertFalse(locationRequested)
        assertFalse(started)
        assertFalse(ended)
    }

    @Test
    fun `handleTagScanned location error does not trigger start or end`() = runTest {
        var started = false
        var ended = false
        var locationError: String? = null

        handleTagScanned(
            event = AppEvent.TagScanned("ABC123", ScanSource.EXTERNAL_RFID),
            currentState = NfcSignInUiState.Initial,
            signInMode = SignInMode.START_ORDER,
            endOderInfo = null,
            onLocationRequest = { LocationRequestResult.Error("location unavailable") },
            onLocationError = { message -> locationError = message },
            onStartOrder = { _, _, _ -> started = true },
            onEndOrder = { _, _, _, _ -> ended = true },
        )

        assertEquals("location unavailable", locationError)
        assertFalse(started)
        assertFalse(ended)
    }

    @Test
    fun `reduceReaderUiState ignores non-active-source reader events`() {
        val unchangedFromConnection = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderConnectionChanged(connected = true, source = ScanSource.SYSTEM_NFC),
            currentReaderState = ReaderUiState.Disconnected,
        )
        val unchangedFromError = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderError(message = "system error", source = ScanSource.SYSTEM_NFC),
            currentReaderState = ReaderUiState.Ready,
        )

        assertTrue(unchangedFromConnection is ReaderUiState.Disconnected)
        assertTrue(unchangedFromError is ReaderUiState.Ready)
    }

    @Test
    fun `handleTagScanned ignores event when ui is success`() = runTest {
        var locationRequested = false
        var started = false
        var ended = false

        handleTagScanned(
            event = AppEvent.TagScanned("ABC123", ScanSource.EXTERNAL_RFID),
            currentState = NfcSignInUiState.Success(),
            signInMode = SignInMode.START_ORDER,
            endOderInfo = null,
            onLocationRequest = {
                locationRequested = true
                LocationRequestResult.Coordinates("121.47", "31.23")
            },
            onLocationError = { error("unexpected location error: $it") },
            onStartOrder = { _, _, _ -> started = true },
            onEndOrder = { _, _, _, _ -> ended = true },
        )

        assertFalse(locationRequested)
        assertFalse(started)
        assertFalse(ended)
    }
}
