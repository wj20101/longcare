package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.User
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
            LongCareTheme {
                Box(modifier = Modifier.width(328.dp)) {
                    TopHeader(
                        user = User(userName = "Mock用户", userIdentity = 1),
                        companyName = "浙江省杭州市长护智慧养老服务科技有限公司"
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_top_company_name").assertExists()
        composeRule.onNodeWithTag("home_top_user_name").assertExists()
        composeRule.onNodeWithTag("home_top_user_identity").assertExists()
        composeRule.onNodeWithTag("home_top_avatar").assertExists()

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
}
