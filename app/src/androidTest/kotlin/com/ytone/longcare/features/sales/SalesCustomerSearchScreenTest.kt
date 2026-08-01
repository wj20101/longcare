package com.ytone.longcare.features.sales

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentListModel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesCustomerSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingKeywordSwitchesToAllAndTriggersGlobalSearchWithoutImeAction() {
        val requests = CopyOnWriteArrayList<Pair<String, Int>>()
        val expectedRequest = "zzzz" to UserLatentCheckState.ALL
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerListScreen(
                    customers = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    loadMoreErrorMessage = null,
                    initialKeyword = "",
                    initialCheckState = UserLatentCheckState.NOT_SUBMITTED,
                    onBack = {},
                    onSearch = { keyword, checkState ->
                        requests += keyword to checkState
                    },
                    onLoadMore = {},
                    onCustomerClick = {},
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextReplacement("zzzz")

        composeRule.onNodeWithText("全部").assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            requests.contains(expectedRequest)
        }

        assertTrue(requests.contains(expectedRequest))
    }

    @Test
    fun selectingStatusTabClearsGlobalKeywordAndFiltersByStatus() {
        val requests = CopyOnWriteArrayList<Pair<String, Int>>()
        val expectedRequest = "" to UserLatentCheckState.PENDING_REVIEW
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerListScreen(
                    customers = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    loadMoreErrorMessage = null,
                    initialKeyword = "ios",
                    initialCheckState = UserLatentCheckState.ALL,
                    onBack = {},
                    onSearch = { keyword, checkState ->
                        requests += keyword to checkState
                    },
                    onLoadMore = {},
                    onCustomerClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText("待审核")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            requests.contains(expectedRequest)
        }

        assertTrue(requests.contains(expectedRequest))
    }

    @Test
    fun reachingListEndRequestsTheNextPage() {
        val loadMoreCalls = AtomicInteger(0)
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerListScreen(
                    customers =
                        listOf(
                            UserLatentListModel(
                                id = 1,
                                userName = "分页测试客户",
                            )
                        ),
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    loadMoreErrorMessage = null,
                    initialKeyword = "",
                    initialCheckState = UserLatentCheckState.ALL,
                    onBack = {},
                    onSearch = { _, _ -> },
                    onLoadMore = { loadMoreCalls.incrementAndGet() },
                    onCustomerClick = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 3_000) {
            loadMoreCalls.get() > 0
        }

        assertEquals(1, loadMoreCalls.get())
    }
}
