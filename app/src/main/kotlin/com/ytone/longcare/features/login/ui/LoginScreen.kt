package com.ytone.longcare.features.login.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.showLongToast
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.navigation.navigateToHomeFromLogin
import com.ytone.longcare.navigation.navigateToWebView
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
import com.ytone.longcare.debug.NfcTestConfig
import com.ytone.longcare.features.facecapture.FaceCaptureTestLauncher
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.navigation.navigateToCamera
import com.ytone.longcare.navigation.navigateToFaceVerificationWithAutoSign
import com.ytone.longcare.navigation.navigateToNfcTest
import com.ytone.longcare.navigation.navigateToManualFaceCapture


@Composable
fun LoginScreen(
    navController: NavController, viewModel: LoginViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    val userAgreementToast = stringResource(R.string.login_user_agreement_toast)
    val privacyPolicyToast = stringResource(R.string.login_privacy_policy_toast)
    var phoneNumber by remember { mutableStateOf(viewModel.getLastLoginPhoneNumber()) }
    var verificationCode by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsStateWithLifecycle()

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Small Logo
            Image(
                painter = painterResource(R.drawable.app_logo_small),
                contentDescription = stringResource(R.string.login_small_logo_description),
                modifier = Modifier
                    .width(86.dp)
                    .align(Alignment.TopStart)
                    .padding(top = 20.dp)
            )

            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo_name),
                contentDescription = stringResource(R.string.login_app_logo_description),
                modifier = Modifier
                    .width(200.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )

            val startConfigState by viewModel.startConfigState.collectAsStateWithLifecycle()

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (NfcTestConfig.ENABLE_NFC_TEST) 16.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                ) {
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Verification Code Input Field
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.login_verification_code_hint),
                                    color = TextColorHint,
                                    fontSize = 15.sp
                                )
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontSize = 15.sp, color = TextColorPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(verificationCodeFocusRequester)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Verification Code Button
                        SendVerificationCodeButton(
                            modifier = Modifier.padding(bottom = 18.dp),
                            viewModel = viewModel,
                            onSendCodeClick = { viewModel.sendSmsCode(phoneNumber) }
                        )
                    }

                    // Login Button
                    Button(
                        onClick = { viewModel.login(phoneNumber, verificationCode) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = phoneNumber.length == maxPhoneLength && verificationCode.isNotEmpty() && loginState !is LoginUiState.Loading,
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

                Spacer(modifier = Modifier.height(48.dp))

                AgreementText(
                    onUserAgreementClick = {
                        when (val state = startConfigState) {
                            is com.ytone.longcare.features.login.vm.StartConfigUiState.Success -> {
                                if (state.data.userXieYiUrl.isNotEmpty()) {
                                    navController.navigateToWebView(
                                        url = state.data.userXieYiUrl,
                                        title = ""
                                    )
                                } else {
                                    context.showLongToast(userAgreementToast)
                                }
                            }
                            else -> context.showLongToast(userAgreementToast)
                        }
                    },
                    onPrivacyPolicyClick = {
                        when (val state = startConfigState) {
                            is com.ytone.longcare.features.login.vm.StartConfigUiState.Success -> {
                                if (state.data.yinSiXieYiUrl.isNotEmpty()) {
                                    navController.navigateToWebView(
                                        url = state.data.yinSiXieYiUrl,
                                        title = ""
                                    )
                                } else {
                                    context.showLongToast(privacyPolicyToast)
                                }
                            }
                            else -> context.showLongToast(privacyPolicyToast)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )

                // Test Buttons (只在测试模式下显示)
                if (NfcTestConfig.ENABLE_NFC_TEST) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val buttonColors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.1f))
                        val buttonShape = RoundedCornerShape(8.dp)

                        Button(
                            onClick = { navController.navigateToNfcTest() },
                            shape = buttonShape,
                            colors = buttonColors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "碰一碰测试",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = {
                                val mockWatermarkData = WatermarkData(
                                    title = "服务前",
                                    insuredPerson = "张三",
                                    caregiver = "李四",
                                    address = "北京市朝阳区xx路xx号"
                                )
                                navController.navigateToCamera(mockWatermarkData)
                            },
                            shape = buttonShape,
                            colors = buttonColors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "相机测试",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { navController.navigateToFaceVerificationWithAutoSign() },
                            shape = buttonShape,
                            colors = buttonColors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "人脸验证测试",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { FaceCaptureTestLauncher.launch(context) },
                            shape = buttonShape,
                            colors = buttonColors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "人脸采集测试",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = { navController.navigateToManualFaceCapture() },
                            shape = buttonShape,
                            colors = buttonColors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "手动人脸捕获",
                                color = PrimaryBlue,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
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
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsStateWithLifecycle()
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
