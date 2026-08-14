package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraPermissionRecoveryPolicyTest {
    @Test
    fun `first permission request stays in runtime permission flow`() {
        assertThat(
            CameraPermissionRecoveryPolicy.shouldOpenSettings(
                wasRequested = false,
                shouldShowRationale = false,
            ),
        ).isFalse()
    }

    @Test
    fun `ordinary denial allows another explained permission request`() {
        assertThat(
            CameraPermissionRecoveryPolicy.shouldOpenSettings(
                wasRequested = true,
                shouldShowRationale = true,
            ),
        ).isFalse()
    }

    @Test
    fun `permanent denial recovers through application settings`() {
        assertThat(
            CameraPermissionRecoveryPolicy.shouldOpenSettings(
                wasRequested = true,
                shouldShowRationale = false,
            ),
        ).isTrue()
    }
}
