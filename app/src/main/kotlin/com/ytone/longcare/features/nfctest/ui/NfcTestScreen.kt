package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.debug.NfcTestConfig
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.nfctest.vm.NfcTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTestScreen(
    actions: NfcTestActions
) {
    val nfcTestViewModel: NfcTestViewModel = hiltViewModel()
    val context = LocalContext.current

    // 获取NfcTestHelper实例
    val nfcTestHelper = if (NfcTestConfig.ENABLE_NFC_TEST) {
        nfcTestViewModel.getHelper()
    } else null
    
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // NFC测试功能管理
    if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
        BindNfcTestLifecycle(
            enabled = true,
            context = context,
            lifecycleOwner = lifecycleOwner,
            onEnable = nfcTestViewModel::enableNfcTest,
            onDisable = nfcTestViewModel::disableNfcTest
        )
    }

    Scaffold(
        topBar = { NfcTestTopBar(onNavigateBack = actions.onNavigateBack) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        NfcTestBody(
            enabled = NfcTestConfig.ENABLE_NFC_TEST,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
    
    // NFC标签检测弹窗
    if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
        nfcTestHelper.NfcTagDialog()
    }
}
