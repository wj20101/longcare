package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceCaptureStabilityGateTest {
    @Test
    fun `capture becomes ready only after duration and sample thresholds`() {
        val gate = FaceCaptureStabilityGate(
            requiredStableDurationMillis = 750L,
            requiredQualifiedSamples = 4,
        )

        val first = gate.evaluate(quality = 0.91f, timestampMillis = 1_000L)
        val second = gate.evaluate(quality = 0.92f, timestampMillis = 1_250L)
        val third = gate.evaluate(quality = 0.93f, timestampMillis = 1_500L)
        val fourth = gate.evaluate(quality = 0.94f, timestampMillis = 1_750L)

        assertThat(first.isReadyToCapture).isFalse()
        assertThat(second.isReadyToCapture).isFalse()
        assertThat(third.isReadyToCapture).isFalse()
        assertThat(fourth.isReadyToCapture).isTrue()
        assertThat(fourth.confirmationProgress).isEqualTo(1f)
    }

    @Test
    fun `elapsed time alone cannot replace consecutive qualified samples`() {
        val gate = FaceCaptureStabilityGate(
            requiredStableDurationMillis = 750L,
            requiredQualifiedSamples = 4,
        )

        gate.evaluate(quality = 0.91f, timestampMillis = 1_000L)
        val result = gate.evaluate(quality = 0.92f, timestampMillis = 2_000L)

        assertThat(result.isQualified).isTrue()
        assertThat(result.isReadyToCapture).isFalse()
        assertThat(result.confirmationProgress).isEqualTo(0.5f)
    }

    @Test
    fun `unqualified frame resets accumulated stability`() {
        val gate = FaceCaptureStabilityGate(
            requiredStableDurationMillis = 600L,
            requiredQualifiedSamples = 3,
        )

        gate.evaluate(quality = 0.91f, timestampMillis = 1_000L)
        gate.evaluate(quality = 0.92f, timestampMillis = 1_300L)
        val reset = gate.evaluate(quality = 0.8f, timestampMillis = 1_400L)
        val restarted = gate.evaluate(quality = 0.93f, timestampMillis = 2_000L)

        assertThat(reset.isQualified).isFalse()
        assertThat(reset.confirmationProgress).isEqualTo(0f)
        assertThat(restarted.isReadyToCapture).isFalse()
        assertThat(restarted.confirmationProgress).isEqualTo(0f)
    }
}
