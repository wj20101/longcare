package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.feature.login.ext.maxPhoneLength
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.theme.InputFieldBackground
import com.ytone.longcare.theme.InputFieldBorderColor
import com.ytone.longcare.theme.PrimaryBlue
import com.ytone.longcare.theme.TextColorHint
import com.ytone.longcare.theme.TextColorPrimary

@Composable
internal fun BoxScope.LoginBrandingHeader() {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.app_logo_small),
        contentDescription = stringResource(R.string.login_small_logo_description),
        modifier = Modifier
            .width(86.dp)
            .padding(top = 20.dp)
            .align(Alignment.TopStart)
    )

    androidx.compose.foundation.Image(
        painter = painterResource(id = R.drawable.app_logo_name),
        contentDescription = stringResource(R.string.login_app_logo_description),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .width(200.dp)
            .padding(top = 80.dp)
    )
}

@Composable
internal fun LoginInputForm(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    verificationCodeFocusRequester: FocusRequester,
    viewModel: LoginViewModel,
    loginState: LoginUiState,
    onSendCodeClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            placeholder = {
                Text(stringResource(R.string.login_phone_number_hint), color = TextColorHint, fontSize = 15.sp)
            },
            shape = RoundedCornerShape(50),
            colors = loginInputFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = TextStyle(fontSize = 15.sp, color = TextColorPrimary),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = verificationCode,
                onValueChange = onVerificationCodeChange,
                placeholder = {
                    Text(
                        stringResource(R.string.login_verification_code_hint),
                        color = TextColorHint,
                        fontSize = 15.sp
                    )
                },
                shape = RoundedCornerShape(50),
                colors = loginInputFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 15.sp, color = TextColorPrimary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(verificationCodeFocusRequester)
            )

            Spacer(modifier = Modifier.width(8.dp))

            SendVerificationCodeButton(
                modifier = Modifier.padding(bottom = 18.dp),
                viewModel = viewModel,
                onSendCodeClick = onSendCodeClick
            )
        }

        Button(
            onClick = onLoginClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            enabled = phoneNumber.length == maxPhoneLength &&
                verificationCode.isNotEmpty() &&
                loginState !is LoginUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
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
