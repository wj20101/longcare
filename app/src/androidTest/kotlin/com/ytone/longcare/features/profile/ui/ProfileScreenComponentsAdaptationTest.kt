package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenComponentsAdaptationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profile_header_and_stats_card_restore_stable_compact_layout() {
        composeRule.setContent {
            LongCareTheme {
                Box(modifier = Modifier.width(328.dp)) {
                    Column {
                        UserInfoSection(
                            user = CurrentUser(
                                scopeKey = UserScopeKey(1, 2, 3),
                                userName = "Mock用户-这是一个用于窄屏适配验证的超长用户名",
                                headUrl = "",
                                userIdentity = 1,
                                gender = 0,
                            )
                        )
                        StatsCard(
                            actions = ProfileActions(
                                onNavigateToHaveServiceUserList = {},
                                onNavigateToNoServiceUserList = {},
                                onOpenUserAgreement = {},
                                onOpenPrivacyPolicy = {}
                            ),
                            stats = NurseServiceTimeModel(
                                haveServiceTime = 1230,
                                haveServiceNum = 15,
                                noServiceTime = 450
                            )
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("profile_user_name").assertExists()
        composeRule.onNodeWithTag("profile_user_identity").assertExists()
        composeRule.onNodeWithTag("profile_user_avatar").assertExists()
        composeRule.onNodeWithTag("profile_stats_card_row").assertExists()

        val userNameBounds = composeRule.onNodeWithTag("profile_user_name").fetchSemanticsNode().boundsInRoot
        val userIdentityBounds = composeRule.onNodeWithTag("profile_user_identity").fetchSemanticsNode().boundsInRoot
        val avatarBounds = composeRule.onNodeWithTag("profile_user_avatar").fetchSemanticsNode().boundsInRoot
        val statsBounds = composeRule.onNodeWithTag("profile_stats_card_row").fetchSemanticsNode().boundsInRoot
        val minStatsHeightPx = with(composeRule.density) { 88.dp.toPx() }
        val userNameLayoutResults = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("profile_user_name").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { action -> action(userNameLayoutResults) }

        assertTrue(userNameBounds.left >= avatarBounds.right)
        assertTrue(userIdentityBounds.left >= avatarBounds.right)
        assertTrue(userNameLayoutResults.single().lineCount == 1)
        assertTrue(statsBounds.height >= minStatsHeightPx)
    }
}
