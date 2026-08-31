package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.core.ui.navigation.AdaptiveAppNavigationScaffold
import com.ytone.longcare.core.ui.navigation.AppNavigationItem
import com.ytone.longcare.theme.LongCareTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAdaptiveNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun medium_width_uses_vertical_navigation_rail() {
        setNavigationContent(size = DpSize(700.dp, 800.dp), fontScale = 1f)

        assertVerticalNavigation()
    }

    @Test
    fun expanded_large_font_keeps_vertical_navigation_reachable() {
        setNavigationContent(size = DpSize(1_000.dp, 700.dp), fontScale = 1.4f)

        assertVerticalNavigation()
    }

    private fun setNavigationContent(size: DpSize, fontScale: Float) {
        composeRule.setContent { HomeNavigationTestContent(size, fontScale) }
        composeRule.onNodeWithTag(DASHBOARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(NURSING_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PROFILE_TAG).assertIsDisplayed()
    }

    private fun assertVerticalNavigation() {
        val dashboard = bounds(DASHBOARD_TAG)
        val nursing = bounds(NURSING_TAG)
        val profile = bounds(PROFILE_TAG)

        assertTrue(dashboard.center.y < nursing.center.y)
        assertTrue(nursing.center.y < profile.center.y)
        assertTrue(abs(dashboard.center.x - nursing.center.x) < 1f)
        assertTrue(abs(nursing.center.x - profile.center.x) < 1f)
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}

@androidx.compose.runtime.Composable
internal fun HomeNavigationTestContent(size: DpSize, fontScale: Float) {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.ForcedSize(size) then
            DeviceConfigurationOverride.FontScale(fontScale),
    ) {
        LongCareTheme {
            AdaptiveAppNavigationScaffold(
                items = listOf(
                    AppNavigationItem("首页", DASHBOARD_TAG),
                    AppNavigationItem("护理", NURSING_TAG),
                    AppNavigationItem("我的", PROFILE_TAG),
                ),
                selectedItemIndex = 0,
                onItemSelected = {},
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

internal const val DASHBOARD_TAG = "home_navigation_dashboard"
internal const val NURSING_TAG = "home_navigation_nursing"
internal const val PROFILE_TAG = "home_navigation_profile"
