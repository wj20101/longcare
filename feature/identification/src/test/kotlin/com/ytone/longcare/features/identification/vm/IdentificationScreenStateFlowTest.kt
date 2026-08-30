package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentificationScreenStateFlowTest {
    @Test
    fun `initial state is a snapshot of every source flow`() = runTest {
        val sources = Sources()
        sources.identification.value = IdentificationState.SERVICE_VERIFIED
        sources.verificationType.value = VerificationType.SERVICE_PERSON
        sources.faceVerification.value = FaceVerificationState.Verifying
        sources.photoUpload.value = PhotoUploadState.Uploading
        sources.faceSetup.value = FaceSetupState.UpdatingServer
        sources.actions.value = listOf(testAction())
        sources.launchRequest.value = testLaunchRequest()

        val state = sources.create(backgroundScope)

        assertEquals(IdentificationState.SERVICE_VERIFIED, state.value.identificationState)
        assertEquals(VerificationType.SERVICE_PERSON, state.value.currentVerificationType)
        assertEquals(FaceVerificationState.Verifying, state.value.faceVerificationState)
        assertEquals(PhotoUploadState.Uploading, state.value.photoUploadState)
        assertEquals(FaceSetupState.UpdatingServer, state.value.faceSetupState)
        assertEquals(sources.actions.value, state.value.pendingUiActions)
        assertEquals(sources.launchRequest.value, state.value.faceSdkLaunchRequest)
    }

    @Test
    fun `active collector receives a consistent aggregate after source updates`() = runTest {
        val sources = Sources()
        val state = sources.create(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()

        sources.identification.value = IdentificationState.ELDER_VERIFIED
        sources.verificationType.value = VerificationType.ELDER
        sources.faceVerification.value = FaceVerificationState.Cancelled
        sources.photoUpload.value = PhotoUploadState.Success
        sources.faceSetup.value = FaceSetupState.Success
        sources.actions.value = listOf(testAction())
        sources.launchRequest.value = testLaunchRequest()
        runCurrent()

        assertEquals(IdentificationState.ELDER_VERIFIED, state.value.identificationState)
        assertEquals(VerificationType.ELDER, state.value.currentVerificationType)
        assertEquals(FaceVerificationState.Cancelled, state.value.faceVerificationState)
        assertEquals(PhotoUploadState.Success, state.value.photoUploadState)
        assertEquals(FaceSetupState.Success, state.value.faceSetupState)
        assertEquals(1, state.value.pendingUiActions.size)
        assertEquals(11L, state.value.faceSdkLaunchRequest?.id)
    }

    @Test
    fun `collector restart catches up with sources changed while sharing was stopped`() = runTest {
        val sources = Sources()
        val state = sources.create(backgroundScope)
        val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.collect()
        }
        runCurrent()
        firstCollector.cancelAndJoin()
        advanceTimeBy(5_001)
        runCurrent()

        sources.identification.value = IdentificationState.ELDER_VERIFIED
        sources.launchRequest.value = testLaunchRequest()
        assertEquals(IdentificationState.INITIAL, state.value.identificationState)
        assertNull(state.value.faceSdkLaunchRequest)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()

        assertEquals(IdentificationState.ELDER_VERIFIED, state.value.identificationState)
        assertEquals(11L, state.value.faceSdkLaunchRequest?.id)
    }

    private class Sources {
        val identification = MutableStateFlow(IdentificationState.INITIAL)
        val verificationType = MutableStateFlow<VerificationType?>(null)
        val faceVerification = MutableStateFlow<FaceVerificationState>(FaceVerificationState.Idle)
        val photoUpload = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Initial)
        val faceSetup = MutableStateFlow<FaceSetupState>(FaceSetupState.Initial)
        val actions = MutableStateFlow<List<IdentificationUiAction>>(emptyList())
        val launchRequest = MutableStateFlow<IdentificationFaceSdkLaunchRequest?>(null)

        fun create(scope: kotlinx.coroutines.CoroutineScope) = createIdentificationScreenUiState(
            scope = scope,
            identificationState = identification,
            currentVerificationType = verificationType,
            faceVerificationState = faceVerification,
            photoUploadState = photoUpload,
            faceSetupState = faceSetup,
            pendingUiActions = actions,
            faceSdkLaunchRequest = launchRequest,
        )
    }

    private fun testAction() = IdentificationUiAction(
        id = 3L,
        effect = IdentificationUiEffect.ShowMessage("queued"),
    )

    private fun testLaunchRequest() = IdentificationFaceSdkLaunchRequest(
        id = 11L,
        config = FaceVerificationConfig("app", "secret", "licence"),
        request = FaceVerificationRequest("name", "id", "order", "user"),
    )
}
