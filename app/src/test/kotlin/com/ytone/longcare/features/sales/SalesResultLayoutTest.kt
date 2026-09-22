package com.ytone.longcare.features.sales

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SalesResultLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun submissionNormalText() = verifyButtons(isSubmission = true)
    @Test fun submissionLargeText() = verifyButtons(isSubmission = true, fontScale = 2f)
    @Test fun submissionShortScreen() = verifyButtons(isSubmission = true, height = 320)
    @Test fun evaluationNormalText() = verifyButtons(isSubmission = false)
    @Test fun evaluationLargeText() = verifyButtons(isSubmission = false, fontScale = 2f)
    @Test fun evaluationShortScreen() = verifyButtons(isSubmission = false, height = 320)

    private fun verifyButtons(isSubmission: Boolean, fontScale: Float = 1f, height: Int = 600) {
        var primaryClicks = 0
        var returnClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                LongCareTheme {
                    Box(Modifier.requiredSize(320.dp, height.dp)) {
                        if (isSubmission) {
                            SalesSubmitSuccessScreen(
                                onBack = { returnClicks++ },
                                onEvaluation = { primaryClicks++ },
                            )
                        } else {
                            SalesEvaluationCompleteScreen(
                                hasReport = true,
                                grade = "A级",
                                onBack = {},
                                onDone = { returnClicks++ },
                                onOpenReport = { primaryClicks++ },
                            )
                        }
                    }
                }
            }
        }
        val primaryText = if (isSubmission) "进行评估" else "确认并提交评估结果"
        val primary = compose.onNodeWithText(primaryText)
        val back = compose.onNodeWithText(if (isSubmission) "确认并返回" else "完成")
        back.performScrollTo().assertIsDisplayed()
        primary.assertIsDisplayed()
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(primaryText, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertEquals(1, textLayouts.size)
        val layout = textLayouts.single()
        // Paragraph width can retain the available width while Text wraps its measured width.
        // Check the actual lines rather than treating that width difference as clipped text.
        assertFalse(layout.multiParagraph.didExceedMaxLines)
        assertEquals(primaryText.length, layout.getLineEnd(layout.lineCount - 1))
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
        }
        assertTrue(layout.multiParagraph.height <= layout.size.height + 1f)
        val primaryBounds = primary.fetchSemanticsNode().boundsInRoot
        val backBounds = back.fetchSemanticsNode().boundsInRoot
        assertEquals(primaryBounds.left, backBounds.left, 0.5f)
        assertEquals(primaryBounds.width, backBounds.width, 0.5f)
        assertEquals(10f, backBounds.top - primaryBounds.bottom, 0.5f)
        primary.performClick()
        back.performClick()
        compose.runOnIdle {
            assertEquals(1, primaryClicks)
            assertEquals(1, returnClicks)
        }
    }
}
