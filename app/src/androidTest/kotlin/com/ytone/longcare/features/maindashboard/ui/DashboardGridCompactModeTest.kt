package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.theme.LongCareTheme
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

        composeRule.onNodeWithText("待护理计划").assertExists()
        composeRule.onNodeWithText("已服务记录").assertExists()
        composeRule.onNodeWithText("你有1个护理待执行").assertExists()
        composeRule.onNodeWithText("查看过往服务记录").assertExists()

        val pendingBounds = composeRule.onNodeWithTag("dashboard_pending_card").fetchSemanticsNode().boundsInRoot
        val recordBounds = composeRule.onNodeWithTag("dashboard_records_card").fetchSemanticsNode().boundsInRoot

        assertEquals(pendingBounds.top, recordBounds.top, 0.5f)
        assertEquals(pendingBounds.bottom, recordBounds.bottom, 0.5f)
    }
}
