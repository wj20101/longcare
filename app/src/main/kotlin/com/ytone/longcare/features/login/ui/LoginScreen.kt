package com.ytone.longcare.features.login.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.showLongToast
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.navigation.navigateToHomeFromLogin
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.InputFieldBackground
import com.ytone.longcare.theme.InputFieldBorderColor
import com.ytone.longcare.theme.LinkColor
import com.ytone.longcare.theme.PrimaryBlue
import com.ytone.longcare.theme.TextColorHint
import com.ytone.longcare.theme.TextColorPrimary
import com.ytone.longcare.theme.TextColorSecondary
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.ytone.longcare.features.login.ext.maxPhoneLength


@Composable
fun LoginScreen(
    navController: NavController, viewModel: LoginViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsState()

    val verificationCodeFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用 Image 作为背景
        Image(
            painter = painterResource(id = R.drawable.login_bg),
            contentDescription = stringResource(R.string.login_background_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop // 或者 ContentScale.FillBounds，根据需要选择
        )

        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // 创建所有UI元素的引用
            val (smallLogo, logo, phoneField, codeField, sendCodeButton, loginButton, agreementText) = createRefs()

            val horizontalMargin = 48.dp

            // Small Logo
            Image(
                painter = painterResource(R.drawable.app_logo_small),
                contentDescription = stringResource(R.string.login_small_logo_description),
                modifier = Modifier
                    .width(86.dp)
                    .constrainAs(smallLogo) {
                        top.linkTo(parent.top, margin = 20.dp)
                        start.linkTo(parent.start)
                    })

            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo_name),
                contentDescription = stringResource(R.string.login_app_logo_description),
                modifier = Modifier
                    .width(200.dp)
                    .constrainAs(logo) {
                        top.linkTo(parent.top, margin = 80.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
            )

            // Phone Number Input Field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { newValue ->
                    val digitsOnly = newValue.filter { it.isDigit() }
                    if (digitsOnly.length <= maxPhoneLength) {
                        phoneNumber = digitsOnly
                    }
                },
                placeholder = {
                    Text(stringResource(R.string.login_phone_number_hint), color = TextColorHint, fontSize = 15.sp)
                },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputFieldBackground,
                    unfocusedContainerColor = InputFieldBackground,
                    disabledContainerColor = InputFieldBackground,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = InputFieldBorderColor,
                    cursorColor = PrimaryBlue
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(fontSize = 15.sp, color = TextColorPrimary),
                modifier = Modifier.constrainAs(phoneField) {
                    start.linkTo(parent.start, margin = horizontalMargin)
                    end.linkTo(parent.end, margin = horizontalMargin)
                    bottom.linkTo(codeField.top, margin = 12.dp)
                    width = Dimension.fillToConstraints // 宽度填充约束
                })

            // Send Verification Code Button
            SendVerificationCodeButton(
                modifier = Modifier.constrainAs(sendCodeButton) {
                    bottom.linkTo(phoneField.bottom)
                    end.linkTo(parent.end, margin = horizontalMargin)
                    bottom.linkTo(loginButton.top, margin = 18.dp)
                },
                viewModel = viewModel, // 传入 ViewModel
                onSendCodeClick = {
                    viewModel.sendSmsCode(phoneNumber)
                }
            )

            // Verification Code Input Field
            OutlinedTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it },
                placeholder = { Text(stringResource(R.string.login_verification_code_hint), color = TextColorHint, fontSize = 15.sp) },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputFieldBackground,
                    unfocusedContainerColor = InputFieldBackground,
                    disabledContainerColor = InputFieldBackground,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = InputFieldBorderColor,
                    cursorColor = PrimaryBlue
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 15.sp, color = TextColorPrimary),
                modifier = Modifier.constrainAs(codeField) {
                    start.linkTo(parent.start, margin = horizontalMargin)
                    end.linkTo(sendCodeButton.start, margin = 8.dp) // 结束于发送按钮的开始处
                    bottom.linkTo(loginButton.top)
                    width = Dimension.fillToConstraints // 宽度填充约束
                    centerVerticallyTo(sendCodeButton) // 简便的垂直对齐方式
                }.focusRequester(verificationCodeFocusRequester)
            )

            // Login Button
            Button(
                onClick = { viewModel.login(phoneNumber, verificationCode) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = phoneNumber.length == maxPhoneLength && verificationCode.isNotEmpty() && loginState !is LoginUiState.Loading,
                modifier = Modifier
                    .height(48.dp) // 明确按钮高度
                    .constrainAs(loginButton) {
                        start.linkTo(parent.start, margin = horizontalMargin)
                        end.linkTo(parent.end, margin = horizontalMargin)
                        bottom.linkTo(agreementText.top, margin = 48.dp)
                        width = Dimension.fillToConstraints // 宽度填充约束
                    }) {
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

            // Agreement Text
            AgreementText( // AgreementText 组件复用之前的实现
                onUserAgreementClick = { context.showLongToast(context.getString(R.string.login_user_agreement_toast)) },
                onPrivacyPolicyClick = { context.showLongToast(context.getString(R.string.login_privacy_policy_toast)) },
                modifier = Modifier.constrainAs(agreementText) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start, margin = 32.dp) // 应用边距以控制文本块宽度
                    end.linkTo(parent.end, margin = 32.dp)     // 应用边距
                    width = Dimension.fillToConstraints // 确保文本在约束内正确换行和居中
                })
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Success) {
            navController.navigateToHomeFromLogin()
        }
    }

    LaunchedEffect(sendSmsState) {
        if (sendSmsState is SendSmsCodeUiState.Success) {
            verificationCodeFocusRequester.requestFocus()
        }
    }
}


@Composable
fun SendVerificationCodeButton(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel, // 传入 ViewModel
    onSendCodeClick: () -> Unit // 点击发送验证码时触发的回调
) {
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsState()
    val isCountingDown = countdownSeconds > 0

    TextButton(
        onClick = {
            if (!isCountingDown) {
                onSendCodeClick() // 实际发送验证码的逻辑，现在由 ViewModel 处理
            }
        },
        shape = RoundedCornerShape(50),
        modifier = modifier,
        enabled = !isCountingDown && sendSmsState !is SendSmsCodeUiState.Loading // 当不在倒计时中时，按钮可用
    ) {
        if (isCountingDown) {
            Text(
                text = stringResource(R.string.login_resend_code_countdown, countdownSeconds),
                color = TextColorHint, // 倒计时期间使用灰色文字
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
            color = TextColorSecondary, fontSize = 12.sp, textAlign = TextAlign.Center // 文本本身居中
        ),
        onTextLayout = { result ->
            textLayoutResult = result
        },
        modifier = modifier // Modifier 从 constrainAs 传入, 已包含 fillMaxWidth 效果由 width = Dimension.fillToConstraints 提供
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    textLayoutResult?.let { layoutResult ->
                        val clickedOffset = layoutResult.getOffsetForPosition(offset)
                        annotatedString.getStringAnnotations(
                            tag = userAgreementTag, start = clickedOffset, end = clickedOffset
                        ).firstOrNull()?.let {
                            onUserAgreementClick()
                        }
                        annotatedString.getStringAnnotations(
                            tag = privacyPolicyTag, start = clickedOffset, end = clickedOffset
                        ).firstOrNull()?.let {
                            onPrivacyPolicyClick()
                        }
                    }
                }
            })
}

@Preview
@Composable
fun AgreementTextPreview() {
    LongCareTheme {
        AgreementText(
            onUserAgreementClick = {},
            onPrivacyPolicyClick = {}
        )
    }
}