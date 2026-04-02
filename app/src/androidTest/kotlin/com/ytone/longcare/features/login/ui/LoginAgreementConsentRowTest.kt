package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class LoginAgreementConsentRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkbox_toggles_only_when_checkbox_itself_is_tapped() {
        var userAgreementClickCount = 0
        var privacyPolicyClickCount = 0
        var agreementTextLayoutResult: TextLayoutResult? = null
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            LongCareTheme {
                AgreementConsentSection(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    onUserAgreementClick = { userAgreementClickCount++ },
                    onPrivacyPolicyClick = { privacyPolicyClickCount++ },
                    onAgreementTextLayout = { agreementTextLayoutResult = it }
                )
            }
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()

        val agreementNode = composeRule.onNodeWithTag("login_agreement_text")
        val layoutResult = checkNotNull(agreementTextLayoutResult) {
            "Agreement text layout result should be reported"
        }
        val prefixBounds = layoutResult.getBoundingBox(0)
        val nodeBounds = agreementNode.getBoundsInRoot()
        val density = composeRule.density
        val tapPosition = Offset(
            with(density) { nodeBounds.left.toPx() } + prefixBounds.center.x,
            with(density) { nodeBounds.top.toPx() } + prefixBounds.center.y
        )
        agreementNode.performTouchInput {
            down(tapPosition)
            up()
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
        composeRule.runOnIdle {
            assertEquals(0, userAgreementClickCount)
            assertEquals(0, privacyPolicyClickCount)
        }
    }
}
