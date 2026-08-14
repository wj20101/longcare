package com.ytone.longcare.features.facecapture

import androidx.camera.core.CameraSelector
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraSelectorPolicyTest {
    @Test
    fun `front camera is preferred when available`() {
        val selectors = availableCameraSelectors { selector ->
            selector === CameraSelector.DEFAULT_FRONT_CAMERA ||
                selector === CameraSelector.DEFAULT_BACK_CAMERA
        }

        assertThat(selectors).containsExactly(
            CameraSelector.DEFAULT_FRONT_CAMERA,
            CameraSelector.DEFAULT_BACK_CAMERA,
        ).inOrder()
    }

    @Test
    fun `rear camera is retained as the standard fallback`() {
        val selectors = availableCameraSelectors { selector ->
            selector === CameraSelector.DEFAULT_BACK_CAMERA
        }

        assertThat(selectors).containsExactly(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    @Test
    fun `external only device receives an unfiltered fallback selector`() {
        var availabilityChecks = 0

        val selectors = availableCameraSelectors {
            availabilityChecks += 1
            availabilityChecks == 3
        }

        assertThat(availabilityChecks).isEqualTo(3)
        assertThat(selectors).hasSize(1)
        assertThat(selectors.single()).isNotSameInstanceAs(CameraSelector.DEFAULT_FRONT_CAMERA)
        assertThat(selectors.single()).isNotSameInstanceAs(CameraSelector.DEFAULT_BACK_CAMERA)
    }

    @Test
    fun `no camera produces an empty candidate list`() {
        assertThat(availableCameraSelectors { false }).isEmpty()
    }
}
