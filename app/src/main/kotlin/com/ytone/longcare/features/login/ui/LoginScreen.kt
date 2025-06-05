package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.login.viewmodel.LoginViewModel
import com.ytone.longcare.navigation.navigateToHomeFromLogin
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.InputFieldBackground
import com.ytone.longcare.ui.InputFieldBorderColor
import com.ytone.longcare.ui.LinkColor
import com.ytone.longcare.ui.PrimaryBlue
import com.ytone.longcare.ui.TextColorHint
import com.ytone.longcare.ui.TextColorPrimary
import com.ytone.longcare.ui.TextColorSecondary

// 最大手机号码长度，用于控制输入长度
private const val maxPhoneLength = 11

@Composable
fun LoginScreen(
    navController: NavController, viewModel: LoginViewModel = hiltViewModel()
) {
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用 Image 作为背景
        Image(
            painter = painterResource(id = R.drawable.login_bg),
            contentDescription = "Login Background",
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
                contentDescription = "small logo",
                modifier = Modifier
                    .width(86.dp)
                    .constrainAs(smallLogo) {
                        top.linkTo(parent.top, margin = 20.dp)
                        start.linkTo(parent.start)
                    })

            // Logo
            InfinityLogo(
                modifier = Modifier
                    .width(200.dp)
                    .constrainAs(logo) {
                        top.linkTo(parent.top, margin = 80.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })

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
                    Text("请输入您的手机号码", color = TextColorHint, fontSize = 15.sp)
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
            TextButton(
                onClick = { /* TODO: Handle send code */ },
                shape = RoundedCornerShape(50),
                modifier = Modifier.constrainAs(sendCodeButton) {
                    bottom.linkTo(phoneField.bottom) // 假设输入框高度约48dp，保证垂直对齐
                    end.linkTo(parent.end, margin = horizontalMargin)
                    bottom.linkTo(loginButton.top, margin = 18.dp)
                    // 高度通过 TextButton 内部内容自适应，或显式设置
                }) {
                Text("发送验证码", color = PrimaryBlue, fontSize = 15.sp)
            }


            // Verification Code Input Field
            OutlinedTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it },
                placeholder = { Text("输入验证码", color = TextColorHint, fontSize = 15.sp) },
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
                })

            // Login Button
            Button(
                onClick = { navController.navigateToHomeFromLogin() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = phoneNumber.length == maxPhoneLength && verificationCode.isNotEmpty(),
                modifier = Modifier
                    .height(48.dp) // 明确按钮高度
                    .constrainAs(loginButton) {
                        start.linkTo(parent.start, margin = horizontalMargin)
                        end.linkTo(parent.end, margin = horizontalMargin)
                        bottom.linkTo(agreementText.top, margin = 48.dp)
                        width = Dimension.fillToConstraints // 宽度填充约束
                    }) {
                Text(
                    "确定登录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Agreement Text
            AgreementText( // AgreementText 组件复用之前的实现
                onUserAgreementClick = { /* TODO: Navigate to User Agreement */ },
                onPrivacyPolicyClick = { /* TODO: Navigate to Privacy Policy */ },
                modifier = Modifier.constrainAs(agreementText) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start, margin = 32.dp) // 应用边距以控制文本块宽度
                    end.linkTo(parent.end, margin = 32.dp)     // 应用边距
                    width = Dimension.fillToConstraints // 确保文本在约束内正确换行和居中
                })
        }
    }
}

// InfinityLogo Composable - 与之前版本相同
@Composable
fun InfinityLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.app_logo_name),
        contentDescription = "App Logo",
        modifier = modifier
    )
}

// AgreementText Composable - 与之前修正后的版本相同
@Composable
fun AgreementText(
    modifier: Modifier = Modifier,
    onUserAgreementClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    val userAgreementTag = "USER_AGREEMENT"
    val privacyPolicyTag = "PRIVACY_POLICY"

    val annotatedString = buildAnnotatedString {
        append("登录即表明已阅读并同意")
        pushStringAnnotation(tag = userAgreementTag, annotation = "user_agreement_link")
        withStyle(style = SpanStyle(color = LinkColor, fontWeight = FontWeight.Normal)) {
            append("《用户协议》")
        }
        pop()
        append("和")
        pushStringAnnotation(tag = privacyPolicyTag, annotation = "privacy_policy_link")
        withStyle(style = SpanStyle(color = LinkColor, fontWeight = FontWeight.Normal)) {
            append("《隐私政策》")
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

@Preview(showBackground = true, device = "spec:width=360dp,height=740dp")
@Composable
fun LoginScreenPreview() {
    LongCareTheme {
        LoginScreen(navController = rememberNavController())
    }
}