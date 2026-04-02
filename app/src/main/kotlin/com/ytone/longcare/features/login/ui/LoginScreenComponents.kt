package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
    val userAgreementTag = "USER_AGREEMENT"
    val privacyPolicyTag = "PRIVACY_POLICY"

    val annotatedString = buildAnnotatedString {
        append(stringResource(R.string.login_agreement_prefix))
        pushStringAnnotation(tag = userAgreementTag, annotation = "user_agreement_link")
        withStyle(style = SpanStyle(color = LinkColor, fontWeight = FontWeight.Normal)) {
            append(stringResource(R.string.login_user_agreement))
        }
        pop()
        append(stringResource(R.string.login_agreement_and))
        pushStringAnnotation(tag = privacyPolicyTag, annotation = "privacy_policy_link")
        withStyle(style = SpanStyle(color = LinkColor, fontWeight = FontWeight.Normal)) {
            append(stringResource(R.string.login_privacy_policy))
        }
        pop()
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = TextStyle(
            color = TextColorSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        ),
        onTextLayout = { result ->
            textLayoutResult = result
        },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                textLayoutResult?.let { layoutResult ->
                    val clickedOffset = layoutResult.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations(
                        tag = userAgreementTag,
                        start = clickedOffset,
                        end = clickedOffset
                    ).firstOrNull()?.let {
                        onUserAgreementClick()
                    }
                    annotatedString.getStringAnnotations(
                        tag = privacyPolicyTag,
                        start = clickedOffset,
                        end = clickedOffset
                    ).firstOrNull()?.let {
                        onPrivacyPolicyClick()
                    }
                }
            }
        }
    )
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
        AgreementText(
            onUserAgreementClick = onUserAgreementClick,
            onPrivacyPolicyClick = onPrivacyPolicyClick,
            modifier = Modifier.weight(1f)
        )
    }
}
