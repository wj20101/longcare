package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.feature.login.R
import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.feature.login.ext.maxPhoneLength
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme

@Composable
internal fun LoginRouteScreen(
    actions: LoginFeatureActions,
    agreementLinks: LoginAgreementLinks,
    viewModel: LoginViewModel,
    initialAgreementChecked: Boolean = false,
) {

    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsStateWithLifecycle()
    val startConfigState by viewModel.startConfigState.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        LoginScreenContent(
            actions = actions,
            agreementLinks = agreementLinks,
            loginState = loginState,
            sendSmsState = sendSmsState,
            startConfigState = startConfigState,
            countdownSeconds = countdownSeconds,
            initialPhoneNumber = remember { viewModel.getLastLoginPhoneNumber() },
            initialAgreementChecked = initialAgreementChecked,
            onPrivacyAgreementConfirmed = viewModel::onPrivacyAgreementConfirmed,
            onSendCodeClick = { phoneNumber -> viewModel.sendSmsCode(phoneNumber) },
            onLoginClick = { phoneNumber, code -> viewModel.login(phoneNumber, code) },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    LoginSuccessEffect(
        loginState = loginState,
        onLoginSuccess = actions.onLoginSuccess,
    )

    LaunchedEffect(feedback?.id) {
        val currentFeedback = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentFeedback.message)
        viewModel.consumeFeedback(currentFeedback.id)
    }
}

@Composable
internal fun LoginSuccessEffect(
    loginState: LoginUiState,
    onLoginSuccess: () -> Unit,
) {
    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }
}

@Composable
internal fun LoginScreenContent(
    actions: LoginFeatureActions,
    agreementLinks: LoginAgreementLinks,
    loginState: LoginUiState,
    sendSmsState: SendSmsCodeUiState,
    startConfigState: StartConfigUiState,
    countdownSeconds: Int,
    initialPhoneNumber: String = "",
    initialAgreementChecked: Boolean = false,
    onPrivacyAgreementConfirmed: () -> Unit = {},
    onSendCodeClick: (String) -> Unit,
    onLoginClick: (String, String) -> Unit,
) {
    val agreementConfirmMessage = stringResource(R.string.login_agreement_confirm_message)
    val agreementConfirmAction = stringResource(R.string.login_agreement_confirm_action)
    val agreementCancelAction = stringResource(R.string.login_agreement_cancel_action)

    var phoneNumber by remember { mutableStateOf(initialPhoneNumber) }
    var verificationCode by remember { mutableStateOf("") }
    var agreementChecked by rememberSaveable { mutableStateOf(initialAgreementChecked) }
    var showAgreementDialog by rememberSaveable { mutableStateOf(false) }
    var showValidationEntrySheet by rememberSaveable { mutableStateOf(false) }
    val verificationCodeFocusRequester = remember { FocusRequester() }
    val updateAgreementChecked: (Boolean) -> Unit = { checked ->
        agreementChecked = checked
    }

    LaunchedEffect(agreementChecked) {
        if (agreementChecked) {
            onPrivacyAgreementConfirmed()
        }
    }

    val openUserAgreement = {
        val url = resolveUserAgreementUrl(startConfigState, agreementLinks)
        actions.onOpenWebPage(url, "")
    }

    val openPrivacyPolicy = {
        actions.onOpenWebPage(resolvePrivacyPolicyUrl(agreementLinks), "")
    }

    val proceedLogin = {
        onLoginClick(phoneNumber, verificationCode)
    }

    val requestSendCode = {
        if (agreementChecked) {
            onSendCodeClick(phoneNumber)
        } else {
            showAgreementDialog = true
        }
    }

    val submitLogin = {
        if (agreementChecked) {
            proceedLogin()
        } else {
            showAgreementDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.login_bg),
            contentDescription = stringResource(R.string.login_background_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compactWidth = maxWidth < 380.dp
                val compactHeight = maxHeight < 720.dp
                val formHorizontalPadding = if (compactWidth) 24.dp else 48.dp
                val agreementSpacing = if (compactHeight) 24.dp else 48.dp
                val contentBottomPadding = if (compactHeight) 20.dp else 32.dp

                LoginBrandingHeader(
                    isCompactLayout = compactHeight,
                    onMainLogoLongPress = { showValidationEntrySheet = true },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = contentBottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoginInputForm(
                        phoneNumber = phoneNumber,
                        onPhoneNumberChange = { newValue ->
                            val digitsOnly = newValue.filter { it.isDigit() }
                            if (digitsOnly.length <= maxPhoneLength) {
                                phoneNumber = digitsOnly
                            }
                        },
                        verificationCode = verificationCode,
                        onVerificationCodeChange = { verificationCode = it },
                        verificationCodeFocusRequester = verificationCodeFocusRequester,
                        countdownSeconds = countdownSeconds,
                        sendSmsState = sendSmsState,
                        loginState = loginState,
                        horizontalPadding = formHorizontalPadding,
                        isCompactLayout = compactWidth,
                        onSendCodeClick = requestSendCode,
                        onLoginClick = submitLogin
                    )

                    Spacer(modifier = Modifier.height(agreementSpacing))

                    AgreementConsentSection(
                        checked = agreementChecked,
                        onCheckedChange = updateAgreementChecked,
                        onUserAgreementClick = openUserAgreement,
                        onPrivacyPolicyClick = openPrivacyPolicy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )

                }
            }
        }
    }

    if (showAgreementDialog) {
        AlertDialog(
            onDismissRequest = { showAgreementDialog = false },
            text = {
                Text(text = agreementConfirmMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        agreementChecked = true
                        showAgreementDialog = false
                    }
                ) {
                    Text(text = agreementConfirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAgreementDialog = false
                }) {
                    Text(text = agreementCancelAction)
                }
            }
        )
    }

    LoginValidationEntrySheet(
        visible = showValidationEntrySheet,
        validationEntryActions = actions.validationEntryActions,
        onDismiss = { showValidationEntrySheet = false },
    )

    LaunchedEffect(sendSmsState) {
        if (sendSmsState is SendSmsCodeUiState.Success) {
            verificationCodeFocusRequester.requestFocus()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenMainPreview() {
    LongCareTheme {
        LoginScreenContent(
            actions = LoginFeatureActions(
                onLoginSuccess = {},
                onOpenWebPage = { _, _ -> }
            ),
            agreementLinks = LoginAgreementLinks(
                userAgreementUrl = "https://example.test/user-agreement",
                privacyPolicyUrl = "https://example.test/privacy-policy",
            ),
            loginState = LoginUiState.Idle,
            sendSmsState = SendSmsCodeUiState.Idle,
            startConfigState = StartConfigUiState.Idle,
            countdownSeconds = 0,
            onSendCodeClick = {},
            onLoginClick = { _, _ -> }
        )
    }
}
