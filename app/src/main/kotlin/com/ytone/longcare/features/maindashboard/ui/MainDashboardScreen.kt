package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.viewmodel.MainDashboardViewModel
import com.ytone.longcare.theme.LongCareTheme

@Composable
fun MainDashboardScreen(
    navController: NavController, mainDashboardViewModel: MainDashboardViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF468AFF), Color(0xFFF6F9FF))
                    )
                )
                .padding(top = paddingValues.calculateTopPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 项目之间的垂直间距
        ) {
            // 顶部Header
            item {
                Spacer(modifier = Modifier.height(8.dp)) // 顶部留出一些空间
                TopHeader()
            }

            // Banner广告卡片
            item {
                HomeBannerCard()
            }

            // 快捷功能区
            item {
                QuickActionsSection()
            }

            // 统计学习区
            item {
                StatsSection()
            }

            // 待服务计划列表
            item {
                Text(
                    text = "待服务计划",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            // 模拟的服务计划数据
            val servicePlans = listOf(
                ServicePlan("孙天成", 8, "助浴服务", "杭州市西湖区328弄24号"),
                ServicePlan("李小梅", 8, "助洁服务", "杭州市滨江区江南大道100号"),
                ServicePlan("王大爷", 8, "助餐服务", "杭州市上城区解放路56号")
            )
            items(servicePlans) { plan ->
                ServicePlanItem(plan)
            }

            // 底部留出一些空间
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TopHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo
        Image(
            painter = painterResource(R.drawable.app_logo_small_white),
            contentDescription = "Logo",
            modifier = Modifier.height(40.dp)
        )

        // 占位符，将右侧内容推到最右边
        Spacer(modifier = Modifier.weight(1f))

        // 护理员信息
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "张默默", fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "护理员", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 头像
        Image(
            painter = ColorPainter(Color.LightGray), // 使用占位符颜色，实际应加载网络图片
            contentDescription = "护理员头像", modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
fun HomeBannerCard() {
    // 1. Banner 图片素材列表 (使用网络图片URL)
    val sampleBanners = listOf(
        BannerItem(1, "https://images.pexels.com/photos/3768894/pexels-photo-3768894.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1", "Banner 1"),
        BannerItem(2, "https://images.pexels.com/photos/3831847/pexels-photo-3831847.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1", "Banner 2"),
        BannerItem(3, "https://images.pexels.com/photos/4058315/pexels-photo-4058315.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1", "Banner 3")
    )

    ImageBannerPager(
        bannerItems = sampleBanners,
        modifier = Modifier.height(120.dp)
    )
}

@Composable
fun QuickActionsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Favorite,
            iconBgColor = Color(0xFFE8F4FF),
            iconTint = Color(0xFF3A9DFF),
            title = "待护理计划",
            subtitle = "你有12个护理待执行",
            badgeCount = 12
        )
        ActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Favorite,
            iconBgColor = Color(0xFFFFF4E8),
            iconTint = Color(0xFFFF9800),
            title = "已服务记录",
            subtitle = "查看过往服务记录"
        )
    }
}


@Composable
fun StatsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Settings,
            text = "工时: 22322",
            subtext = "服务工时统计"
        )
        InfoCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Settings,
            text = "待学习",
            subtext = "服务标准学习",
            badgeCount = 12
        )
    }
}


// --- 可复用的子组件 ---

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeCount: Int? = null
) {
    Card(
        onClick = { /*TODO*/ },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor), contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (badgeCount != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge(containerColor = Color.Red) { Text("$badgeCount") }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    subtext: String,
    badgeCount: Int? = null
) {
    Card(
        onClick = { /*TODO*/ },
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (badgeCount != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge(containerColor = Color.Red) { Text("$badgeCount") }
                    }
                }
                Text(text = subtext, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// 服务计划列表项的数据类
data class ServicePlan(
    val name: String, val hours: Int, val serviceType: String, val address: String
)

@Composable
fun ServicePlanItem(plan: ServicePlan) {
    Card(
        onClick = { /*TODO*/ },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = plan.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F4FF)
                    ) {
                        Text(
                            text = "工时: ${plan.hours}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${plan.serviceType} 地址: ${plan.address}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "详情",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// --- Preview ---
@Preview
@Composable
fun MainDashboardScreenPreview() {
    LongCareTheme {
        MainDashboardScreen(navController = rememberNavController())
    }
}
