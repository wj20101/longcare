package com.ytone.longcare.features.sales

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.UserLatentCheckState
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesCustomerSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingKeywordTriggersSearchWithoutImeAction() {
        val requests = CopyOnWriteArrayList<Pair<String, Int>>()
        val expectedRequest = "zzzz" to UserLatentCheckState.NOT_SUBMITTED
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerListScreen(
                    customers = emptyList(),
                    isLoading = false,
                    initialKeyword = "",
                    initialCheckState = UserLatentCheckState.NOT_SUBMITTED,
                    onBack = {},
                    onSearch = { keyword, checkState ->
                        requests += keyword to checkState
                    },
                    onCustomerClick = {},
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextReplacement("zzzz")

        composeRule.waitUntil(timeoutMillis = 3_000) {
            requests.contains(expectedRequest)
        }

        assertTrue(requests.contains(expectedRequest))
    }
}
