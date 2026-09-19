package com.ytone.longcare.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.LoginViewModel
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState

@Composable
internal fun AssistantPrivacyScreen(onAccept: () -> Unit, onReject: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AssistantPage(stringResource(R.string.assistant_privacy_title)) {
        Text(stringResource(R.string.assistant_privacy_notice))
        TextButton(onClick = { uriHandler.openUri("https://www.ytone.com.cn/longcare/xieyi/yinsi.html") }) {
            Text(stringResource(R.string.assistant_privacy_policy))
        }
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_agree)) }
        OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_disagree)) }
    }
}

@Composable
internal fun AssistantHomeScreen(
    loggedIn: Boolean,
    result: String,
    photoUri: String,
    onClear: () -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    onOpen: (AssistantTool) -> Unit,
) {
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    AssistantPage(stringResource(R.string.app_name)) {
        Text(stringResource(R.string.assistant_isolation_notice), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = if (loggedIn) onLogout else onLogin) {
            Text(stringResource(if (loggedIn) R.string.assistant_logout else R.string.assistant_login))
        }
        AssistantTool.entries.forEach { tool ->
            val label = when (tool) {
                AssistantTool.DEFAULT_FACE -> R.string.assistant_default_face
                AssistantTool.NFC -> R.string.assistant_nfc
                AssistantTool.CAMERA -> R.string.assistant_camera
                AssistantTool.TENCENT_FACE -> R.string.assistant_tencent_face
                AssistantTool.MANUAL_FACE -> R.string.assistant_manual_face
            }
            FilledTonalButton(onClick = { onOpen(tool) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.assistant_location_notice), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }) { Text(stringResource(R.string.assistant_location_permission)) }
        if (result.isNotBlank() || photoUri.isNotBlank()) {
            HorizontalDivider()
            Text(stringResource(R.string.assistant_last_result), style = MaterialTheme.typography.titleMedium)
            if (result.isNotBlank()) Text(result)
            if (photoUri.isNotBlank()) {
                AsyncImage(model = photoUri, contentDescription = stringResource(R.string.assistant_photo_preview),
                    modifier = Modifier.fillMaxWidth().height(280.dp))
                Text(photoUri, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.assistant_clear)) }
        }
    }
}

@Composable
internal fun AssistantLoginScreen(onBack: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val login by viewModel.loginState.collectAsStateWithLifecycle()
    val sms by viewModel.sendSmsCodeState.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf(viewModel.getLastLoginPhoneNumber()) }
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.onPrivacyAgreementConfirmed() }
    AssistantLoginContent(
        phone = phone, code = code,
        onPhoneChange = { phone = it.take(11) }, onCodeChange = { code = it.take(10) },
        login = login, sms = sms, countdown = countdown, feedback = feedback?.message,
        onSendCode = { viewModel.sendSmsCode(phone) }, onLogin = { viewModel.login(phone, code) },
        onBack = onBack,
    )
}

@Composable
internal fun AssistantLoginContent(
    phone: String, code: String,
    onPhoneChange: (String) -> Unit, onCodeChange: (String) -> Unit,
    login: LoginUiState, sms: SendSmsCodeUiState, countdown: Int, feedback: String?,
    onSendCode: () -> Unit, onLogin: () -> Unit, onBack: () -> Unit,
) {
    AssistantPage(stringResource(R.string.assistant_login), onBack) {
        Text(stringResource(R.string.assistant_login_notice))
        OutlinedTextField(value = phone, onValueChange = onPhoneChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            label = { Text(stringResource(R.string.assistant_phone)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = code, onValueChange = onCodeChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            label = { Text(stringResource(R.string.assistant_sms_code)) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onSendCode, enabled = countdown == 0 && sms !is SendSmsCodeUiState.Loading) {
            Text(if (countdown > 0) stringResource(R.string.assistant_sms_countdown, countdown) else stringResource(R.string.assistant_send_sms))
        }
        Button(onClick = onLogin, enabled = login !is LoginUiState.Loading) {
            Text(stringResource(if (login is LoginUiState.Loading) R.string.assistant_logging_in else R.string.assistant_login))
        }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
