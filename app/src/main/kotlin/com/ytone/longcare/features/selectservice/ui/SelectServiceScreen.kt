package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.ui.bgGradientBrush

// --- 数据模型 ---
data class ServiceItem(
    val id: String, val name: String, val duration: Int, // 分钟
    var isSelected: Boolean = false
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectServiceScreen() {

    val initialServiceItems = remember {
        listOf(
            ServiceItem(id = "1", name = "助浴服务", duration = 12, isSelected = true),
            ServiceItem(id = "2", name = "助浴服务", duration = 12, isSelected = true),
            ServiceItem(id = "3", name = "助浴服务", duration = 12),
            ServiceItem(id = "4", name = "助浴服务", duration = 12),
            ServiceItem(id = "5", name = "助浴服务", duration = 12)
        )
    }
    // 使用 mutableStateListOf 来使其内部元素的改变能够触发 recomposition
    val serviceItems =
        remember { mutableStateListOf<ServiceItem>().apply { addAll(initialServiceItems) } }
    val totalDuration = remember(serviceItems) {
        serviceItems.filter { it.isSelected }.sumOf { it.duration }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                    Text(
                        "请选择服务项目", fontWeight = FontWeight.Bold
                    )
                }, navigationIcon = {
                    IconButton(onClick = { /* TODO: 返回操作 */ }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
                )
            }, containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                TotalDurationDisplay(totalDuration = totalDuration)

                Spacer(modifier = Modifier.height(24.dp))

                ServiceSelectionList(
                    serviceItems = serviceItems, onItemClick = { clickedIndex ->
                        // 创建一个新的列表副本并修改选中状态，以触发 recomposition
                        val currentItem = serviceItems[clickedIndex]
                        serviceItems[clickedIndex] =
                            currentItem.copy(isSelected = !currentItem.isSelected)
                    })

                Spacer(modifier = Modifier.weight(1f))

                NextStepButton(
                    text = "下一步",
                    // 按钮是否可用可以根据是否有选中项来判断
                    enabled = serviceItems.any { it.isSelected },
                    onClick = { /* TODO: 执行下一步操作 */ })
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun TotalDurationDisplay(totalDuration: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${totalDuration}分钟",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本次服务总工时", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun ServiceSelectionList(
    serviceItems: List<ServiceItem>, onItemClick: (Int) -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp) // 列表项之间的间距
    ) {
        itemsIndexed(serviceItems) { index, item ->
            ServiceSelectionItem(
                item = item, onClick = { onItemClick(index) })
        }
    }
}

@Composable
fun ServiceSelectionItem(item: ServiceItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.duration}分钟",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            // 自定义勾选图标
            Icon(
                imageVector = if (item.isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (item.isSelected) stringResource(R.string.common_selected) else stringResource(R.string.common_unselected),
                tint = if (item.isSelected) Color(0xFF34C759) else Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun NextStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5CA0FF), Color(0xFF2A8CFF)) // 根据设计图调整渐变色
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Gray, // 确保禁用时渐变也可见 (或设置特定禁用渐变)
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// --- 预览 ---
@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun SelectServiceScreenPreview() {
    MaterialTheme { // 建议包裹在您的应用主题中
        SelectServiceScreen()
    }
}