package com.ytone.longcare.features.nursingexecution.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.ui.bgGradientBrush

// --- 数据模型 (如果需要) ---
data class ClientInfo(
    val name: String,
    val age: Int,
    val idNumber: String,
    val address: String,
    val serviceContent: String
)

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingExecutionScreen() {
    val clientInfo = ClientInfo(
        name = "孙连中",
        age = 72,
        idNumber = "310023023020320302",
        address = "浙江省杭州市西湖区爱家小区32号501",
        serviceContent = "助浴"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("护理执行", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: 返回操作 */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent, // TopAppBar 浅蓝色背景
                        titleContentColor = Color.White, navigationIconContentColor = Color.White
                    )
                )
            }, containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "请阅读并确认订单服务内容以及客户信息，扫描客户容貌验证通过后，开始服务。",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box {
                    ClientInfoCard(modifier = Modifier.padding(top = 8.dp), clientInfo = clientInfo)

                    SectionTitleTag(title = "客户信息")
                }

                Spacer(modifier = Modifier.weight(1f)) // 将按钮推到底部

                ConfirmButton(text = "确认信息开始人脸认证", onClick = { /* TODO */ })

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

}

@Composable
fun SectionTitleTag(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
    ) {
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.service_tab_bg), // <-- 替换为您的PNG资源ID
            contentDescription = null, // 背景图片通常不需要内容描述
            modifier = Modifier.size(120.dp, 44.dp), contentScale = ContentScale.FillBounds
        )

        // 文字内容
        Text(
            text = title,
            color = Color(0xFF134AA8),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.Center) // 文字在Box中垂直居中，水平靠左
                .offset(y = -(6.dp))
        )
    }
}


@Composable
fun ClientInfoCard(modifier: Modifier, clientInfo: ClientInfo) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp) // 行之间的间距
        ) {
            InfoRow(label = "姓名:", value = clientInfo.name)
            InfoRow(label = "年龄:", value = clientInfo.age.toString())
            InfoRow(label = "身份证号:", value = clientInfo.idNumber)
            InfoRow(label = "地址:", value = clientInfo.address)
            InfoRow(label = "服务内容:", value = clientInfo.serviceContent)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top // 顶部对齐，以防地址过多行
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp) // 给标签一个固定宽度
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f) // 值占据剩余空间
        )
    }
}

@Composable
fun ConfirmButton(text: String, onClick: () -> Unit) {
    val gradientBrush = Brush.horizontalGradient(colors = listOf(Color(0xFF2B83FF), Color(0xFF3192FD)))

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = gradientBrush, shape = RoundedCornerShape(50)),
        shape = RoundedCornerShape(50), // 按钮本身的形状，用于点击涟漪效果等
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // 非常重要！
            contentColor = Color.White // 文字颜色设置为白色，以在渐变背景上可见
        ),
        contentPadding = PaddingValues() // 移除默认的内边距，因为我们将自己处理内容布局
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White // 确保文字颜色与渐变背景对比明显
        )
    }
}

// --- 预览 ---
@Preview(showBackground = true)
@Composable
fun NursingExecutionScreenPreview() {
    MaterialTheme { // 建议包裹在您的应用主题中
        NursingExecutionScreen()
    }
}