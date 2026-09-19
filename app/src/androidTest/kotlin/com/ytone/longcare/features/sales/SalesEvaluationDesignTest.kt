package com.ytone.longcare.features.sales

import android.graphics.Bitmap
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.unit.dp
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUiState
import com.ytone.longcare.integration.qlz.QlzFingerContacts
import com.ytone.longcare.navigation.NavigationStateTestActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs

/** Deterministic design states; no backend, credentials or BLE hardware. */
class SalesEvaluationDesignTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()

    private fun show(state: QlzEvaluationUiState) {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { SalesEvaluationGuideScreen(state, onBack = {}, onRetry = {}) }
        compose.onNodeWithText("手握检测").assertIsDisplayed()
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "evaluation-$name.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        // Opt-in inspection window for android-cli; normal test runs do not wait.
        val pause = InstrumentationRegistry.getArguments().getString("capturePauseMs")
            ?.toLongOrNull()?.coerceIn(0, 45_000) ?: 0
        if (pause > 0) Thread.sleep(pause)
    }

    @Test fun gripDesignShowsThreeGreenAndTwoGrayContacts() {
        show(QlzEvaluationUiState(stage = QlzEvaluationStage.MEASURING,
            fingerContacts = QlzFingerContacts(true, true, true, false, false)))
        compose.onNodeWithText("请按照示意的方式握住设备").assertIsDisplayed()
        repeat(5) { index ->
            compose.onNodeWithTag("qlz_finger_$index").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    if (index < 3) "接触良好" else "尚未接触"))
        }
        compose.onNodeWithTag("qlz_progress_card").assertDoesNotExist()
        snapshot("grip")
    }

    @Test fun readyDesignShowsFiveContactsAndPreparationCountdown() {
        show(QlzEvaluationUiState(stage = QlzEvaluationStage.MEASURING,
            fingerContacts = QlzFingerContacts(true, true, true, true, true), preparationSeconds = 5))
        compose.onNodeWithText("手握完成，请不要松开手").assertIsDisplayed()
        compose.onNodeWithTag("qlz_grip_countdown").assertIsDisplayed()
        repeat(5) { index ->
            compose.onNodeWithTag("qlz_finger_$index").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "接触良好"))
        }
        compose.onNodeWithTag("qlz_progress_card").assertDoesNotExist()
        snapshot("ready")
    }

    @Test fun measuringDesignShowsActualPercentageAndSingleProgressCard() {
        show(QlzEvaluationUiState(stage = QlzEvaluationStage.MEASURING,
            successCount = 46, totalCount = 100, showMeasurementProgress = true))
        compose.onNodeWithText("46%").assertIsDisplayed()
        compose.onNodeWithText("检测中").assertIsDisplayed()
        compose.onNodeWithTag("qlz_measurement_progress").assertIsDisplayed()
        compose.onNodeWithTag("qlz_finger_0").assertDoesNotExist()
        snapshot("measuring")
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun progressFillIsProportionalAndTrackHasNoGap() {
        val progress = mutableStateOf(0f)
        compose.setContent {
            EvaluationProgressBar(progress.value, Modifier.size(248.dp, 19.dp).testTag("progress"))
        }
        val green = Color(0xFF63E544).toArgb()
        val track = Color(0xFFE5EDFF).toArgb()
        for (percent in listOf(0, 1, 46, 100)) {
            compose.runOnIdle { progress.value = percent / 100f }
            val pixels = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
            val middle = pixels.height / 2
            val greenPixels = (0 until pixels.width).count { pixels[it, middle].toArgb() == green }
            assertTrue("$percent% fill is $greenPixels/${pixels.width}",
                abs(greenPixels - pixels.width * percent / 100f) <= 2f)
            if (percent == 0) assertEquals(track, pixels[pixels.width / 2, middle].toArgb())
            if (percent == 46) {
                // At the rounded fill tip the full track must remain underneath, never white.
                val boundary = (pixels.width * 0.46f).toInt()
                assertEquals(track, pixels[boundary, pixels.height / 4].toArgb())
                assertEquals(track, pixels[boundary + 1, pixels.height / 4].toArgb())
            }
        }
    }
}
