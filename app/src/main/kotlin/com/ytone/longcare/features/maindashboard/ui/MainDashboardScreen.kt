package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.maindashboard.vm.MainDashboardViewModel
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.models.protos.User
import com.ytone.longcare.navigation.AppDestinations
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.components.UserAvatar

@Composable
fun MainDashboardScreen(
    navController: NavController, viewModel: MainDashboardViewModel = hiltViewModel()
) {
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(AppDestinations.HOME_ROUTE)
    }
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel(parentEntry)
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    val todayOrderList by viewModel.todayOrderListState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // 当此 Composable 的生命周期进入 RESUMED 状态时（即回到此页面），
        // 就会执行刷新操作。
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadTodayOrders()
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        // 只有在 user 不为 null (即已登录) 的情况下才显示内容
        user?.let { loggedInUser ->
            MainDashboardContent(
                user = loggedInUser,
                todayOrderList = todayOrderList,
                navController = navController,
                modifier = Modifier.padding(top = paddingValues.calculateTopPadding(), start = 16.dp, end = 16.dp)
            )
        } ?: run {
            // 如果 user 为 null (例如正在登出或初始化)，显示加载指示器
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 将主界面的核心内容提取到一个新的 Composable 中
 * @param user 保证不为空的 User 对象
 */
@Composable
private fun MainDashboardContent(
    user: User,
    todayOrderList: List<TodayServiceOrderModel>,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            TopHeader(user = user)
        }
        item {
            HomeBannerCard()
        }
        item {
            DashboardGridWithImages(pendingCarePlanCount = todayOrderList.count { it.state == 0 })
        }
        val servicePlans = todayOrderList.filter { it.state == 0 }.map {
            ServicePlan(
                name = it.name,
                hours = it.totalServiceTime,
                serviceType = "",
                address = it.liveAddress
            )
        }

        if (servicePlans.isNotEmpty()) {
            item {
                Text(
                    text = "待护理计划",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(servicePlans) { plan ->
                ServicePlanItem(plan)
            }
        }
    }
}

/**
 * 修改 TopHeader，现在它接收一个非空的 User 对象
 * @param user Protobuf 生成的 User 对象，保证非空
 */
@Composable
fun TopHeader(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        ImageWithAdaptiveWidth(
            drawableResId = R.drawable.app_logo_small_white,
            fixedHeight = 34.dp,
            contentDescription = stringResource(R.string.main_dashboard_logo)
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = user.userName, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(
                text = user.userIdentityShow(),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        UserAvatar(avatarUrl = user.headUrl)
    }
}

@Composable
fun HomeBannerCard() {
    // 1. Banner 图片素材列表 (使用网络图片URL)
    val sampleBanners = listOf(
        BannerItem(1, R.drawable.main_banner, "Banner 1")
    )

    ImageBannerPager(
        bannerItems = sampleBanners, modifier = Modifier.height(120.dp)
    )
}

/**
 * 整体仪表盘网格
 * @param pendingCarePlanCount 待护理计划总数
 */
@Composable
fun DashboardGridWithImages(pendingCarePlanCount: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp) // 行之间的间距
    ) {
        // 第一行
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.main_ic_plan,
                title = "待护理计划",
                subtitle = if (pendingCarePlanCount > 0) "你有${pendingCarePlanCount}个护理待执行" else "",
                badgeCount = pendingCarePlanCount,
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.main_ic_records,
                title = "已服务记录",
                subtitle = "查看过往服务记录",
            )
        }
//        // 第二行
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            InfoCard(
//                modifier = Modifier.weight(1f),
//                iconRes = R.drawable.main_ic_hours,
//                title = "工时: 22322",
//                subtitle = "服务工时统计",
//            )
//            InfoCard(
//                modifier = Modifier.weight(1f),
//                iconRes = R.drawable.main_ic_study,
//                title = "待学习",
//                subtitle = "服务标准学习",
//                badgeCount = 12,
//            )
//        }
    }
}


@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    iconContentDescription: String? = null,
) {
    Card(
        onClick = { /* 卡片点击事件 */ },
        modifier = modifier.height(65.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标区域
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = iconContentDescription,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 右侧文字区域
            Column {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeCount != null && badgeCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(
                            containerColor = Color(0xFFFFC107)
                        ) {
                            Text(
                                text = badgeCount.toString(),
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()){
                    // 副标题
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 12.sp,
                        style = TextStyle(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = plan.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = stringResource(R.string.common_details),
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 一个固定高度、宽度根据图片原始比例自适应的 Image 组件。
 *
 * @param drawableResId 要加载的本地 drawable 资源 ID。
 * @param fixedHeight 固定的高度。
 * @param modifier 修饰符。
 * @param contentDescription 图片的内容描述，用于无障碍。
 */
@Composable
fun ImageWithAdaptiveWidth(
    @DrawableRes drawableResId: Int,
    fixedHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // 1. 使用 painterResource 创建 Painter
    val painter = painterResource(id = drawableResId)

    // 2. 从 painter 的 intrinsicSize 中计算宽高比
    //    为防止除以0，增加一个保护判断
    val aspectRatio = if (painter.intrinsicSize.height > 0) {
        painter.intrinsicSize.width / painter.intrinsicSize.height
    } else {
        1.0f // 如果高度为0，则默认为1:1
    }

    // 3. 应用 Modifier 链
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillHeight,
        modifier = modifier
            .height(fixedHeight)      // 首先，强制设置固定的高度
            .aspectRatio(aspectRatio) // 然后，根据宽高比自动计算并设置宽度
    )
}

// --- Preview ---
@Preview
@Composable
fun MainDashboardScreenPreview() {
    LongCareTheme {
        MainDashboardScreen(navController = rememberNavController())
    }
}
