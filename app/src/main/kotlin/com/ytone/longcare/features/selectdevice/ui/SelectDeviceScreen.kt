package com.ytone.longcare.features.selectdevice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.theme.bgButtonGradientBrush
import com.ytone.longcare.features.selectdevice.api.SelectDeviceActions
import com.ytone.longcare.model.OrderKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDeviceScreen(
    actions: SelectDeviceActions,
    orderKey: OrderKey
) {
    // 模拟设备数据
    val devices = remember {
        List(6) { index -> Device(id = "id_$index", name = "设备名称") }
    }
    var selectedDeviceIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.select_device_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp, top = 16.dp)
                    ) {
                        NextStepButton(
                            text = stringResource(R.string.common_next_step),
                            enabled = true,
                            onClick = singleClick { 
                                actions.onStartOrderNfcSignIn(orderKey)
                            }
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.select_device_instruction),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                DeviceGrid(
                    devices = devices,
                    selectedDeviceIndex = selectedDeviceIndex,
                    onDeviceSelected = { index ->
                        selectedDeviceIndex = if (selectedDeviceIndex == index) null else index
                    })

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
