package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.feature.login.ext.maxPhoneLength
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.theme.InputFieldBackground
import com.ytone.longcare.theme.InputFieldBorderColor
import com.ytone.longcare.theme.PrimaryBlue
import com.ytone.longcare.theme.TextColorHint
import com.ytone.longcare.theme.TextColorPrimary

@Composable
internal fun BoxScope.LoginBrandingHeader(
    isCompactLayout: Boolean = false,
    onMainLogoLongPress: (() -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val smallLogoWidth = if (isCompactLayout) 72.dp else 86.dp
    val smallLogoTopPadding = if (isCompactLayout) 12.dp else 20.dp
    val mainLogoWidth = if (isCompactLayout) 160.dp else 200.dp
    val mainLogoTopPadding = if (isCompactLayout) 48.dp else 80.dp

    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.app_logo_small),
        contentDescription = stringResource(R.string.login_small_logo_description),
        modifier = Modifier
            .width(smallLogoWidth)
            .padding(top = smallLogoTopPadding)
            .align(Alignment.TopStart)
    )

    androidx.compose.foundation.Image(
        painter = painterResource(id = R.drawable.app_logo_name),
        contentDescription = stringResource(R.string.login_app_logo_description),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .width(mainLogoWidth)
            .padding(top = mainLogoTopPadding)
            .testTag("login_main_logo")
            .then(
                if (onMainLogoLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMainLogoLongPress()
                        },
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
internal fun LoginInputForm(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    verificationCodeFocusRequester: FocusRequester,
    countdownSeconds: Int,
    sendSmsState: SendSmsCodeUiState,
    loginState: LoginUiState,
    horizontalPadding: Dp,
    isCompactLayout: Boolean,
    onSendCodeClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val inputTextSize = if (isCompactLayout) 14.sp else 15.sp
    val sendCodeButtonMinWidth = if (isCompactLayout) 92.dp else 104.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.login_phone_number_hint),
                    color = TextColorHint,
                    fontSize = inputTextSize,
                    maxLines = 1
                )
            },
            shape = RoundedCornerShape(50),
            colors = loginInputFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = TextStyle(fontSize = inputTextSize, color = TextColorPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_phone_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = verificationCode,
                onValueChange = onVerificationCodeChange,
                placeholder = {
                    Text(
                        stringResource(R.string.login_verification_code_hint),
                        color = TextColorHint,
                        fontSize = inputTextSize,
                        maxLines = 1
                    )
                },
                shape = RoundedCornerShape(50),
                colors = loginInputFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = inputTextSize, color = TextColorPrimary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("login_verification_code_input")
                    .focusRequester(verificationCodeFocusRequester)
            )

            SendVerificationCodeButton(
                modifier = Modifier.widthIn(min = sendCodeButtonMinWidth),
                countdownSeconds = countdownSeconds,
                sendSmsState = sendSmsState,
                onSendCodeClick = onSendCodeClick
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onLoginClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            enabled = phoneNumber.length == maxPhoneLength &&
                verificationCode.isNotEmpty() &&
                loginState !is LoginUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_submit_button")
                .height(48.dp)
        ) {
            Text(
                stringResource(R.string.login_button_text),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            if (loginState is LoginUiState.Loading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun loginInputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = InputFieldBackground,
    unfocusedContainerColor = InputFieldBackground,
    disabledContainerColor = InputFieldBackground,
    focusedBorderColor = PrimaryBlue,
    unfocusedBorderColor = InputFieldBorderColor,
    cursorColor = PrimaryBlue
)
