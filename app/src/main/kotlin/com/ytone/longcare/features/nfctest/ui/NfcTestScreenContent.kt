package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NfcTestTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "碰一碰测试",
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Black,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.Black,
            navigationIconContentColor = Color.Black,
        ),
    )
}

@Composable
internal fun NfcTestBody(
    enabled: Boolean,
    r65cPanelState: R65CHidPanelState,
    onR65CInputChanged: (String) -> Unit,
    onR65CFocusChanged: (Boolean) -> Unit,
    onR65CRequestRefocus: () -> Unit,
    onR65CClearResult: () -> Unit,
    modifier: Modifier = Modifier,
    rawValidationState: R65CHidRawValidationState = R65CHidRawValidationState(),
    onRawTextFieldValueChanged: (String) -> Unit = {},
    onRawFocusChanged: (Boolean) -> Unit = {},
    onRawStartListening: () -> Unit = {},
    onRawStopListening: () -> Unit = {},
    onRawRequestRefocus: () -> Unit = {},
    onRawClearSession: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (enabled) {
            EnabledNfcTestCard()
        } else {
            DisabledNfcTestCard()
        }
        R65CHidInputTestPanel(
            state = r65cPanelState,
            onInputChanged = onR65CInputChanged,
            onFocusChanged = onR65CFocusChanged,
            onRequestRefocus = onR65CRequestRefocus,
            onClearResult = onR65CClearResult,
        )
        R65CHidRawValidationPanel(
            state = rawValidationState,
            onTextFieldValueChanged = onRawTextFieldValueChanged,
            onFocusChanged = onRawFocusChanged,
            onStartListening = onRawStartListening,
            onStopListening = onRawStopListening,
            onRequestRefocus = onRawRequestRefocus,
            onClearSession = onRawClearSession,
        )
    }
}

@Composable
private fun EnabledNfcTestCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "碰一碰ID读取",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "请将碰一碰靠近智能手机进行测试",
                fontSize = 16.sp,
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "功能说明：",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• 检测碰一碰的ID编码\n• 自动复制到剪贴板\n• 显示标签信息弹窗",
                fontSize = 14.sp,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun DisabledNfcTestCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NFC测试功能已禁用",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "请先在登录页长按 Logo 开启测试入口",
                fontSize = 14.sp,
                color = Color.Gray,
            )
        }
    }
}
