package com.ytone.longcare.features.home.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.home.api.resolveHomeExperience
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeExperienceContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountSwitchDoesNotDisplayThePreviousRole() {
        val userIdentity = mutableStateOf<Int?>(2)
        composeRule.setContent {
            HomeExperienceContent(
                experience = resolveHomeExperience(userIdentity.value),
                loadingContent = { Marker(LOADING_TAG) },
                careContent = { Marker(CARE_TAG) },
                salesContent = { Marker(SALES_TAG) },
            )
        }

        composeRule.onNodeWithTag(SALES_TAG).assertIsDisplayed()
        composeRule.runOnIdle { userIdentity.value = null }
        composeRule.onNodeWithTag(LOADING_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SALES_TAG).assertDoesNotExist()

        composeRule.runOnIdle { userIdentity.value = 1 }
        composeRule.onNodeWithTag(CARE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LOADING_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SALES_TAG).assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun Marker(tag: String) {
        Text(
            modifier = Modifier.testTag(tag),
            text = tag,
        )
    }

    private companion object {
        const val LOADING_TAG = "home-loading"
        const val CARE_TAG = "home-care"
        const val SALES_TAG = "home-sales"
    }
}
