package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.core.ui.R as CoreUiR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardGridCompactModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrow_width_keeps_cards_in_one_row_and_shows_subtitles() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pendingTitle =
            targetContext.getString(CoreUiR.string.dashboard_pending_care_plans)
        val pendingSubtitle =
            targetContext.getString(
                R.string.dashboard_pending_count,
                1,
            )
        val recordsTitle =
            targetContext.getString(CoreUiR.string.dashboard_service_records)
        val recordsDescription =
            targetContext.getString(R.string.dashboard_service_records_description)

        composeRule.setContent {
            LongCareTheme {
                Box(modifier = Modifier.width(328.dp)) {
                    DashboardGridWithImages(
                        pendingCarePlanCount = 1,
                        actions = MainDashboardActions(
                            onNavigateToCarePlansList = {},
                            onNavigateToServiceRecordsList = {},
                            onNavigateToNursingExecution = {},
                            onNavigateToService = {},
                            onNavigateToServiceCountdown = { _, _ -> },
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithText(pendingTitle).assertExists()
        composeRule.onNodeWithText(recordsTitle).assertExists()
        composeRule.onNodeWithText(pendingSubtitle).assertExists()
        composeRule.onNodeWithText(recordsDescription).assertExists()

        val pendingBounds = composeRule.onNodeWithTag("dashboard_pending_card").fetchSemanticsNode().boundsInRoot
        val recordBounds = composeRule.onNodeWithTag("dashboard_records_card").fetchSemanticsNode().boundsInRoot

        assertEquals(pendingBounds.top, recordBounds.top, 0.5f)
        assertEquals(pendingBounds.bottom, recordBounds.bottom, 0.5f)
    }
}
