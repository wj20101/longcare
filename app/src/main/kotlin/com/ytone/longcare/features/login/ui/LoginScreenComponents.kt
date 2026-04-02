package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.theme.LinkColor
import com.ytone.longcare.theme.PrimaryBlue
import com.ytone.longcare.theme.TextColorHint
import com.ytone.longcare.theme.TextColorSecondary

@Composable
fun SendVerificationCodeButton(
    modifier: Modifier = Modifier,
    countdownSeconds: Int,
    sendSmsState: SendSmsCodeUiState,
    onSendCodeClick: () -> Unit
) {
    val isCountingDown = countdownSeconds > 0

    TextButton(
        onClick = {
            if (!isCountingDown) {
                onSendCodeClick()
            }
        },
        shape = RoundedCornerShape(50),
        modifier = modifier,
        enabled = !isCountingDown && sendSmsState !is SendSmsCodeUiState.Loading
    ) {
        if (isCountingDown) {
            Text(
                text = stringResource(R.string.login_resend_code_countdown, countdownSeconds),
                color = TextColorHint,
                fontSize = 15.sp
            )
        } else {
            Text(
                text = stringResource(R.string.login_send_code_button_text),
                color = PrimaryBlue,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun AgreementText(
    modifier: Modifier = Modifier,
    onUserAgreementClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    val textStyle = TextStyle(
        color = TextColorSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_agreement_prefix),
            style = textStyle,
            modifier = Modifier.testTag("login_agreement_prefix_text")
        )
        Text(
            text = stringResource(R.string.login_user_agreement),
            style = textStyle.copy(
                color = LinkColor,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.clickable(onClick = onUserAgreementClick)
        )
        Text(
            text = stringResource(R.string.login_agreement_and),
            style = textStyle
        )
        Text(
            text = stringResource(R.string.login_privacy_policy),
            style = textStyle.copy(
                color = LinkColor,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.clickable(onClick = onPrivacyPolicyClick)
        )
    }
}

@Composable
fun AgreementConsentSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onUserAgreementClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("login_agreement_checkbox"),
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryBlue,
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        AgreementText(
            onUserAgreementClick = onUserAgreementClick,
            onPrivacyPolicyClick = onPrivacyPolicyClick,
            modifier = Modifier.testTag("login_agreement_text")
        )
    }
}
