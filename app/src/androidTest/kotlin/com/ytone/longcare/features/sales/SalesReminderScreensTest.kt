package com.ytone.longcare.features.sales

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.ToDoResultModel
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesReminderScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toDoList_displaysApiFieldsAndOpensSelectedItem() {
        val selectedIndex = AtomicInteger(-1)
        composeRule.setContent {
            SalesPageBackground {
                SalesReminderListScreen(
                    reminders =
                        listOf(
                            ToDoResultModel(
                                title = "上门评估",
                                content = "请联系客户确认时间",
                                createTime = "2026-08-01 09:00:00",
                            )
                        ),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onReminderClick = selectedIndex::set,
                )
            }
        }

        composeRule.onNodeWithText("上门评估").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("2026-08-01 09:00:00").assertIsDisplayed()
        assertEquals(0, selectedIndex.get())
    }

    @Test
    fun toDoDetail_displaysApiTitleContentAndReminderTime() {
        composeRule.setContent {
            SalesPageBackground {
                SalesReminderDetailScreen(
                    reminder =
                        ToDoResultModel(
                            title = "回访客户",
                            content = "确认评估报告是否已送达",
                            createTime = "2026-08-02 10:30:00",
                        ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("回访客户").assertIsDisplayed()
        composeRule.onNodeWithText("确认评估报告是否已送达").assertIsDisplayed()
        composeRule.onNodeWithText("2026-08-02 10:30:00").assertIsDisplayed()
    }
}
