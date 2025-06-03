package com.ytone.longcare.features.selectdevice.ui // 包名请根据您的项目结构调整

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 数据模型 ---
data class Device(
    val id: String,
    val name: String,
    // val imageUrl: String? = null // 如果有设备图片
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDeviceScreen() {
    // 1. 定义渐变色背景
    val gradientBrush =
        Brush.verticalGradient(colors = listOf(Color(0xFF468AFF), Color(0xFFF6F9FF)))

    // 模拟设备数据
    val devices = remember {
        List(6) { index -> Device(id = "id_$index", name = "设备名称") }
    }
    var selectedDeviceIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("请选择设备", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: 返回操作 */ }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }, containerColor = Color.Transparent // Scaffold 透明
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp), // 页面左右边距
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "请先选择相应设备，进行碰一碰",
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

                Spacer(modifier = Modifier.weight(1f)) // 将按钮推到底部

                NextStepButton(
                    text = "下一步", enabled = selectedDeviceIndex != null, // 仅当有设备选中时才可用
                    onClick = { /* TODO: 执行下一步操作 */ })

                Spacer(modifier = Modifier.height(32.dp)) // 按钮与屏幕底部的间距
            }
        }
    }
}

@Composable
fun DeviceGrid(
    devices: List<Device>, selectedDeviceIndex: Int?, onDeviceSelected: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape = RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp), // 调整Grid的整体内边距
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(devices) { index, device ->
            DeviceItem( // 调用上面修改后的 DeviceItem
                device = device,
                isSelected = selectedDeviceIndex == index,
                onClick = { onDeviceSelected(index) })
        }
    }
}

@Composable
fun DeviceItem(device: Device, isSelected: Boolean, onClick: () -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxSize() // 填满Card的内部空间
            .padding(vertical = 12.dp, horizontal = 12.dp), // 调整内边距
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上方图片/图标占位符
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .background(
                    color = Color(0xFFEBF5FF), // 占位符的浅灰色背景
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    BorderStroke(width = 1.dp, color = Color(0xFF468AFF)),
                    shape = RoundedCornerShape(8.dp)
                ), contentAlignment = Alignment.Center
        ) {
            // 在这里放置实际的设备图片或图标
            // Icon(painter = painterResource(id = R.drawable.ic_device_placeholder), contentDescription = null)
        }

        Spacer(modifier = Modifier.height(6.dp)) // 图片与文字之间的间距

        // 设备名称
        Text(
            text = device.name,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用稍暗的文字颜色
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}


@Composable
fun NextStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {

    val gradientBrush =
        Brush.horizontalGradient(colors = listOf(Color(0xFF2B83FF), Color(0xFF3192FD)))

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = gradientBrush, shape = RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, disabledContainerColor = Color.Gray
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun SelectDeviceScreenPreview() {
    MaterialTheme { // 建议包裹在您的应用主题中
        SelectDeviceScreen()
    }
}