package com.ytone.longcare.features.home.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCompactNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compact_width_uses_horizontal_bottom_navigation() {
        composeRule.setContent {
            HomeNavigationTestContent(size = DpSize(360.dp, 800.dp), fontScale = 1f)
        }
        composeRule.onNodeWithTag(DASHBOARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(NURSING_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PROFILE_TAG).assertIsDisplayed()

        val dashboard = bounds(DASHBOARD_TAG)
        val nursing = bounds(NURSING_TAG)
        val profile = bounds(PROFILE_TAG)

        assertTrue(dashboard.center.x < nursing.center.x)
        assertTrue(nursing.center.x < profile.center.x)
        assertTrue(abs(dashboard.center.y - nursing.center.y) < 1f)
        assertTrue(abs(nursing.center.y - profile.center.y) < 1f)
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
