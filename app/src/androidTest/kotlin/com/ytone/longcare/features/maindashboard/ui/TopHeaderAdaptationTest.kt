package com.ytone.longcare.features.maindashboard.ui

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
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopHeaderAdaptationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun long_company_name_wraps_without_colliding_with_user_block() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(328.dp, 200.dp)) then
                    DeviceConfigurationOverride.FontScale(1f)
            ) {
                LongCareTheme {
                    TopHeader(
                        user = currentUser("Mock用户"),
                        companyName = "浙江省杭州市长护智慧养老服务科技有限公司"
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_top_company_name").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_user_name").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_user_identity").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_avatar").assertIsDisplayed()

        val companyBounds = composeRule.onNodeWithTag("home_top_company_name").fetchSemanticsNode().boundsInRoot
        val userNameBounds = composeRule.onNodeWithTag("home_top_user_name").fetchSemanticsNode().boundsInRoot
        val userIdentityBounds = composeRule.onNodeWithTag("home_top_user_identity").fetchSemanticsNode().boundsInRoot
        val avatarBounds = composeRule.onNodeWithTag("home_top_avatar").fetchSemanticsNode().boundsInRoot
        val minWrappedHeightPx = with(composeRule.density) { 32.dp.toPx() }
        val userBlockLeft = minOf(userNameBounds.left, userIdentityBounds.left)

        assertTrue(companyBounds.height > minWrappedHeightPx)
        assertTrue(companyBounds.right < userBlockLeft)
        assertTrue(companyBounds.right < avatarBounds.left)
    }

    @Test
    fun compact_large_font_keeps_user_name_on_one_line() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(324.dp, 200.dp)) then
                    DeviceConfigurationOverride.FontScale(1.4f)
            ) {
                LongCareTheme {
                    TopHeader(
                        user = currentUser("Android销售"),
                        companyName = "庆平智慧养老测试",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_top_company_name").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_user_name").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_user_identity").assertIsDisplayed()
        composeRule.onNodeWithTag("home_top_avatar").assertIsDisplayed()

        val userNameBounds =
            composeRule.onNodeWithTag("home_top_user_name").fetchSemanticsNode().boundsInRoot
        val userIdentityBounds =
            composeRule.onNodeWithTag("home_top_user_identity").fetchSemanticsNode().boundsInRoot
        val avatarBounds =
            composeRule.onNodeWithTag("home_top_avatar").fetchSemanticsNode().boundsInRoot
        val companyBounds =
            composeRule.onNodeWithTag("home_top_company_name").fetchSemanticsNode().boundsInRoot
        val maxSingleLineHeightPx = with(composeRule.density) { 32.dp.toPx() }

        assertTrue(userNameBounds.height <= maxSingleLineHeightPx)
        assertTrue(userNameBounds.right < avatarBounds.left)
        assertTrue(userIdentityBounds.right < avatarBounds.left)
        assertTrue(companyBounds.top >= maxOf(userIdentityBounds.bottom, avatarBounds.bottom))
    }

    private fun currentUser(name: String) = CurrentUser(
        scopeKey = UserScopeKey(1, 2, 3),
        userName = name,
        headUrl = "",
        userIdentity = 1,
        gender = 0,
    )
}
