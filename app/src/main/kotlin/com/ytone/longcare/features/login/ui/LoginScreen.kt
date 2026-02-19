package com.ytone.longcare.features.login.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.showLongToast
import com.ytone.longcare.debug.NfcTestConfig
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.feature.login.ext.maxPhoneLength
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState

@Composable
fun LoginScreen(
    actions: LoginFeatureActions,
    viewModel: LoginViewModel = hiltViewModel()
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    val userAgreementToast = stringResource(R.string.login_user_agreement_toast)
    val privacyPolicyToast = stringResource(R.string.login_privacy_policy_toast)

    var phoneNumber by remember { mutableStateOf(viewModel.getLastLoginPhoneNumber()) }
    var verificationCode by remember { mutableStateOf("") }
    val verificationCodeFocusRequester = remember { FocusRequester() }

    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val sendSmsState by viewModel.sendSmsCodeState.collectAsStateWithLifecycle()
    val startConfigState by viewModel.startConfigState.collectAsStateWithLifecycle()

    val openUserAgreement = {
        when (val state = startConfigState) {
            is StartConfigUiState.Success -> {
                if (state.data.userXieYiUrl.isNotEmpty()) {
                    actions.onOpenWebPage(state.data.userXieYiUrl, "")
                } else {
                    context.showLongToast(userAgreementToast)
                }
            }

            else -> context.showLongToast(userAgreementToast)
        }
    }

    val openPrivacyPolicy = {
        when (val state = startConfigState) {
            is StartConfigUiState.Success -> {
                if (state.data.yinSiXieYiUrl.isNotEmpty()) {
                    actions.onOpenWebPage(state.data.yinSiXieYiUrl, "")
                } else {
                    context.showLongToast(privacyPolicyToast)
                }
            }

            else -> context.showLongToast(privacyPolicyToast)
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
            LoginBrandingHeader()

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (NfcTestConfig.ENABLE_NFC_TEST) 16.dp else 32.dp),
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
                    viewModel = viewModel,
                    loginState = loginState,
                    onSendCodeClick = { viewModel.sendSmsCode(phoneNumber) },
                    onLoginClick = { viewModel.login(phoneNumber, verificationCode) }
                )

                Spacer(modifier = Modifier.height(48.dp))

                AgreementText(
                    onUserAgreementClick = openUserAgreement,
                    onPrivacyPolicyClick = openPrivacyPolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )

                if (NfcTestConfig.ENABLE_NFC_TEST) {
                    Spacer(modifier = Modifier.height(32.dp))
                    LoginNfcTestButtons(
                        actions = actions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Success) {
            actions.onLoginSuccess()
        }
    }

    LaunchedEffect(sendSmsState) {
        if (sendSmsState is SendSmsCodeUiState.Success) {
            verificationCodeFocusRequester.requestFocus()
        }
    }
}
