package com.ytone.longcare.features.identification.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdentificationEventEmitterTest {

    @Test
    fun `emitFaceCaptureRequiredEvents emits toast before navigation`() = runTest {
        val collected = mutableListOf<IdentificationEvent>()

        emitFaceCaptureRequiredEvents { event ->
            collected += event
        }

        assertEquals(
            listOf(
                IdentificationEvent.ShowToast("请先设置人脸信息"),
                IdentificationEvent.NavigateToFaceCapture
            ),
            collected
        )
    }
}
