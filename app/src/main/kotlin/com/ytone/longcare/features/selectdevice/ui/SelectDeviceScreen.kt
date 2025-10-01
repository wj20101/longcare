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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.navigation.navigateToNfcSignInForStartOrder
import com.ytone.longcare.theme.bgButtonGradientBrush
import com.ytone.longcare.api.request.OrderInfoRequestModel

// --- 数据模型 ---
data class Device(
    val id: String,
    val name: String
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDeviceScreen(
    navController: NavController = rememberNavController(),
    orderInfoRequest: OrderInfoRequestModel? = null
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
                        IconButton(onClick = { navController.popBackStack() }) {
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

                Spacer(modifier = Modifier.weight(1f)) // 将按钮推到底部

                NextStepButton(
                    text = stringResource(R.string.common_next_step), enabled = true/* selectedDeviceIndex != null*/, // 仅当有设备选中时才可用
                    onClick = { 
                        navController.navigateToNfcSignInForStartOrder(orderInfoRequest ?: OrderInfoRequestModel(orderId = 0L, planId = 0))
                    })

                Spacer(modifier = Modifier.height(32.dp)) // 按钮与屏幕底部的间距
            }
        }
    }
}

@Preview
@Composable
fun SelectDeviceScreenPreview() {
    SelectDeviceScreen(
        orderInfoRequest = OrderInfoRequestModel(orderId = 1L, planId = 0)
    )
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

@Preview
@Composable
fun DeviceGridPreview() {
    val devices = remember {
        List(6) { index -> Device(id = "id_$index", name = "设备名称") }
    }
    var selectedDeviceIndex by remember { mutableStateOf<Int?>(null) }
    DeviceGrid(devices = devices, selectedDeviceIndex = selectedDeviceIndex, onDeviceSelected = { index -> selectedDeviceIndex = if (selectedDeviceIndex == index) null else index })
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

@Preview
@Composable
fun DeviceItemPreview() {
    val device = Device(id = "id_0", name = "设备名称")
    DeviceItem(device = device, isSelected = false, onClick = {})
}

@Composable
fun NextStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = bgButtonGradientBrush, shape = RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, disabledContainerColor = Color.Gray
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Preview
@Composable
fun NextStepButtonPreview() {
    NextStepButton(text = "Next Step", enabled = true, onClick = {})
}

@Preview
@Composable
fun SelectDeviceScreenWithNavControllerPreview() {
    val navController = rememberNavController()
    SelectDeviceScreen(navController = navController, orderInfoRequest = OrderInfoRequestModel(orderId = 12345L, planId = 0))
}
