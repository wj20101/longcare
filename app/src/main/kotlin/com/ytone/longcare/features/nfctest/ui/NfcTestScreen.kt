package com.ytone.longcare.features.nfctest.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.debug.NfcTestConfig
import com.ytone.longcare.di.NursingExecutionEntryPoint
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTestScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        NursingExecutionEntryPoint::class.java
    )
    
    // 获取NfcTestHelper实例
    val nfcTestHelper = if (NfcTestConfig.ENABLE_NFC_TEST) {
        entryPoint.nfcTestHelper()
    } else null
    
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // NFC测试功能管理
    if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
        LaunchedEffect(context) {
            val activity = context as? Activity
            activity?.let {
                nfcTestHelper.enable(it)
            }
        }
        
        // 在Composable销毁时禁用NFC功能
        DisposableEffect(context) {
            onDispose {
                val activity = context as? Activity
                activity?.let {
                    nfcTestHelper.disable(it)
                }
            }
        }
        
        // 监听生命周期变化，确保在页面恢复时重新启用NFC
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val activity = context as? Activity
                activity?.let {
                    nfcTestHelper.enable(it)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NFC测试",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (NfcTestConfig.ENABLE_NFC_TEST) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NFC测试功能",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "请将NFC标签靠近设备进行测试",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "功能说明：",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "• 检测NFC标签ID\n• 自动复制到剪贴板\n• 显示标签信息弹窗",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NFC测试功能已禁用",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "请在NfcTestConfig中启用测试功能",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
    
    // NFC标签检测弹窗
    if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
        nfcTestHelper.NfcTagDialog()
    }
}