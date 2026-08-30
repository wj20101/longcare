package com.ytone.longcare.features.identification.ui

import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationScreenUiState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationScreenModelsTest {
    @Test
    fun `initial state enables service action and blocks elder and next actions`() {
        val render = IdentificationScreenUiState().toRenderState()

        assertEquals(IdentificationCardStatus.ACTION, render.servicePerson.status)
        assertTrue(render.servicePerson.actionEnabled)
        assertEquals(IdentificationCardStatus.ACTION, render.elder.status)
        assertFalse(render.elder.actionEnabled)
        assertFalse(render.nextEnabled)
    }

    @Test
    fun `verified service unlocks elder while verified elder unlocks next`() {
        val service = IdentificationScreenUiState(
            identificationState = IdentificationState.SERVICE_VERIFIED,
        ).toRenderState()
        val elder = IdentificationScreenUiState(
            identificationState = IdentificationState.ELDER_VERIFIED,
        ).toRenderState()

        assertEquals(IdentificationCardStatus.VERIFIED, service.servicePerson.status)
        assertTrue(service.elder.actionEnabled)
        assertFalse(service.nextEnabled)
        assertEquals(IdentificationCardStatus.VERIFIED, elder.servicePerson.status)
        assertEquals(IdentificationCardStatus.VERIFIED, elder.elder.status)
        assertTrue(elder.nextEnabled)
    }

    @Test
    fun `service setup state has priority over face verification state`() {
        val render = IdentificationScreenUiState(
            currentVerificationType = VerificationType.SERVICE_PERSON,
            faceVerificationState = FaceVerificationState.Verifying,
            faceSetupState = FaceSetupState.Error("setup"),
        ).toRenderState()

        assertEquals(IdentificationCardStatus.FACE_SETUP_ERROR, render.servicePerson.status)
        assertEquals(IdentificationScreenEvent.RetryFaceSetup, render.servicePerson.retryEvent())
    }

    @Test
    fun `verification phases and retry events are scoped to active person`() {
        val initializing = IdentificationScreenUiState(
            currentVerificationType = VerificationType.ELDER,
            faceVerificationState = FaceVerificationState.Initializing,
            identificationState = IdentificationState.SERVICE_VERIFIED,
        ).toRenderState()
        val cancelled = IdentificationScreenUiState(
            currentVerificationType = VerificationType.ELDER,
            faceVerificationState = FaceVerificationState.Cancelled,
            identificationState = IdentificationState.SERVICE_VERIFIED,
        ).toRenderState()

        assertEquals(IdentificationCardStatus.VERIFIED, initializing.servicePerson.status)
        assertEquals(IdentificationCardStatus.FACE_INITIALIZING, initializing.elder.status)
        assertEquals(IdentificationCardStatus.FACE_CANCELLED, cancelled.elder.status)
        assertEquals(
            IdentificationScreenEvent.RetryFaceVerification(IdentificationPersonType.ELDER),
            cancelled.elder.retryEvent(),
        )
    }

    @Test
    fun `photo states replace elder action without changing service card`() {
        val processing = IdentificationScreenUiState(
            identificationState = IdentificationState.SERVICE_VERIFIED,
            photoUploadState = PhotoUploadState.Processing,
        ).toRenderState()
        val uploading = IdentificationScreenUiState(
            identificationState = IdentificationState.SERVICE_VERIFIED,
            photoUploadState = PhotoUploadState.Uploading,
        ).toRenderState()

        assertEquals(IdentificationCardStatus.VERIFIED, processing.servicePerson.status)
        assertEquals(IdentificationCardStatus.PHOTO_PROCESSING, processing.elder.status)
        assertEquals(IdentificationCardStatus.PHOTO_UPLOADING, uploading.elder.status)
        assertNull(uploading.elder.retryEvent())
    }

    @Test
    fun `primary events map to the owning person flow`() {
        assertEquals(
            IdentificationScreenEvent.VerifyServicePerson,
            IdentificationPersonType.SERVICE_PERSON.primaryEvent(),
        )
        assertEquals(
            IdentificationScreenEvent.CaptureElderPhoto,
            IdentificationPersonType.ELDER.primaryEvent(),
        )
    }
}
