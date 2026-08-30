package com.ytone.longcare.navigation

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.theme.LongCareTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyConsentProfileTagsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startupProfileTagsAreUniqueVisibleAndAgreeIsInteractive() {
        val agreeCalls = AtomicInteger(0)
        composeRule.setContent {
            LongCareTheme {
                PrivacyConsentDialog(
                    onAgree = { agreeCalls.incrementAndGet() },
                    onDisagree = {},
                )
            }
        }

        listOf(
            "profile_privacy_root",
            "profile_privacy_title",
            "profile_privacy_reject",
            "profile_privacy_accept",
        ).forEach { tag ->
            composeRule.onAllNodesWithTag(tag).assertCountEquals(1)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("profile_privacy_reject").assert(hasClickAction())
        composeRule.onNodeWithTag("profile_privacy_accept")
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, agreeCalls.get())
    }

    @Test
    fun disagreeIsInteractiveAndInvokesRejectBeforeClosingTheActivity() {
        val disagreeCalls = AtomicInteger(0)
        composeRule.setContent {
            LongCareTheme {
                PrivacyConsentDialog(
                    onAgree = {},
                    onDisagree = { disagreeCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag("profile_privacy_reject")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()
        assertEquals(1, disagreeCalls.get())
    }
}
