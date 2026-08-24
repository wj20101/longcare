package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceDetectionStatePublisherTest {
    @Test
    fun `equivalent camera frames emit one ui state`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        repeat(30) {
            publisher.publish(
                detected = true,
                positionQualified = true,
                progress = 0.3f,
                hint = FaceCaptureHint.BLINK,
                timestampMillis = it * 50L,
            )
        }

        assertThat(emissions).containsExactly(
            FaceDetectionSnapshot(
                detected = true,
                positionQualified = true,
                confirmationProgress = 0.3f,
                hint = FaceCaptureHint.BLINK,
            ),
        )
    }

    @Test
    fun `small progress changes are quantized while forward progress emits immediately`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0.301f, FaceCaptureHint.BLINK, 0L)
        publisher.publish(true, true, 0.319f, FaceCaptureHint.BLINK, 50L)
        publisher.publish(true, true, 0.371f, FaceCaptureHint.HOLD_AFTER_BLINK, 100L)

        assertThat(emissions).hasSize(2)
        assertThat(emissions.last().confirmationProgress).isEqualTo(0.35f)
        assertThat(emissions.last().hint).isEqualTo(FaceCaptureHint.HOLD_AFTER_BLINK)
    }

    @Test
    fun `one noisy lost-face frame does not replace the current instruction`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 0L)
        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 50L)
        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 100L)

        assertThat(emissions).hasSize(1)
        assertThat(emissions.single().hint).isEqualTo(FaceCaptureHint.BLINK)
    }

    @Test
    fun `a changed instruction is shown after it remains stable`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 0L)
        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 100L)
        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 300L)
        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 450L)

        assertThat(emissions.map(FaceDetectionSnapshot::hint)).containsExactly(
            FaceCaptureHint.BLINK,
            FaceCaptureHint.NO_FACE,
        ).inOrder()
    }

    @Test
    fun `oscillating position hints never reach the ui`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 0L)
        publisher.publish(true, false, 0f, FaceCaptureHint.FACE_FORWARD, 100L)
        publisher.publish(true, false, 0f, FaceCaptureHint.HEAD_LEVEL, 300L)
        publisher.publish(true, false, 0f, FaceCaptureHint.FACE_FORWARD, 600L)
        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 700L)

        assertThat(emissions).hasSize(1)
        assertThat(emissions.single().hint).isEqualTo(FaceCaptureHint.BLINK)
    }

    @Test
    fun `temporary eye probability regression never moves visible progress backwards`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0.25f, FaceCaptureHint.HOLD_AFTER_BLINK, 0L)
        publisher.publish(true, true, 0.70f, FaceCaptureHint.HOLD_AFTER_BLINK, 50L)
        publisher.publish(true, true, 0.40f, FaceCaptureHint.REOPEN_EYES, 100L)
        publisher.publish(true, true, 0.45f, FaceCaptureHint.HOLD_AFTER_BLINK, 300L)
        publisher.publish(true, true, 0.80f, FaceCaptureHint.HOLD_AFTER_BLINK, 400L)

        assertThat(emissions.map(FaceDetectionSnapshot::confirmationProgress)).containsExactly(
            0.25f,
            0.70f,
            0.80f,
        ).inOrder()
    }

    @Test
    fun `invalid blink progress resets only after the regression persists`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(true, true, 0.70f, FaceCaptureHint.HOLD_AFTER_BLINK, 0L)
        publisher.publish(true, true, 0.40f, FaceCaptureHint.REOPEN_EYES, 100L)
        publisher.publish(true, true, 0.25f, FaceCaptureHint.HOLD_AFTER_BLINK, 400L)
        publisher.publish(true, true, 0f, FaceCaptureHint.BLINK, 550L)

        assertThat(emissions.map(FaceDetectionSnapshot::confirmationProgress)).containsExactly(
            0.70f,
            0f,
        ).inOrder()
    }

    @Test
    fun `reset allows the same state to be delivered for a new camera session`() {
        val emissions = mutableListOf<FaceDetectionSnapshot>()
        val publisher = FaceDetectionStatePublisher(emissions::add)

        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 0L)
        publisher.reset()
        publisher.publish(false, false, 0f, FaceCaptureHint.NO_FACE, 0L)

        assertThat(emissions).hasSize(2)
    }
}
