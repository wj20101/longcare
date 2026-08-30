package com.ytone.longcare.features.serviceorders.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.theme.LongCareTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceRecordsProfileTagsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun baselineProfileTagsAreUniqueVisibleAndBackIsInteractive() {
        val backCalls = AtomicInteger(0)
        composeRule.setContent {
            LongCareTheme {
                ServiceOrdersListScreenLayout(
                    title = "服务记录",
                    emptyTitle = "暂无记录",
                    emptySubtitle = "fixture",
                    filteredOrders = emptyList(),
                    actions = ServiceOrdersListActions(
                        onNavigateBack = { backCalls.incrementAndGet() },
                        onNavigateToNursingExecution = {},
                        onNavigateToService = {},
                    ),
                    profileTagsEnabled = true,
                )
            }
        }

        listOf("profile_service_records_root", "profile_service_records_back").forEach { tag ->
            composeRule.onAllNodesWithTag(tag).assertCountEquals(1)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("profile_service_records_back")
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, backCalls.get())
    }
}
