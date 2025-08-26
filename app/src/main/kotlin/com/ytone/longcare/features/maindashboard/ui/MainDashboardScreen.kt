package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.api.response.isPendingCare
import com.ytone.longcare.common.utils.NavigationHelper
import com.ytone.longcare.di.NursingExecutionEntryPoint
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.theme.IndicatorGradientStart
import com.ytone.longcare.theme.IndicatorGradientEnd
import com.ytone.longcare.features.serviceorders.ui.ServiceOrderItem
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.models.protos.User
import com.ytone.longcare.navigation.HomeRoute
import com.ytone.longcare.navigation.navigateToCarePlansList
import com.ytone.longcare.navigation.navigateToNursingExecution
import com.ytone.longcare.navigation.navigateToService
import com.ytone.longcare.navigation.navigateToServiceRecordsList
import com.ytone.longcare.ui.components.UserAvatar
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.shared.vm.OrderDetailUiState
import kotlinx.coroutines.flow.first
import dagger.hilt.android.EntryPointAccessors

@Composable
fun MainDashboardScreen(
    navController: NavController
) {
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(HomeRoute)
    }
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        NursingExecutionEntryPoint::class.java
    )
    val navigationHelper = entryPoint.navigationHelper()
    val toastHelper = entryPoint.toastHelper()
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel(parentEntry)
    val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
    val sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    val todayOrderList by todayOrderViewModel.todayOrderListState.collectAsStateWithLifecycle()
    val inOrderList by todayOrderViewModel.inOrderListState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // 当此 Composable 的生命周期进入 RESUMED 状态时（即回到此页面），
        // 就会执行刷新操作。
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            todayOrderViewModel.loadTodayOrders()
            todayOrderViewModel.loadInOrders()
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
                inOrderList = inOrderList,
                navController = navController,
                homeSharedViewModel = homeSharedViewModel,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding()
                ),
                navigationHelper = navigationHelper,
                sharedOrderDetailViewModel = sharedOrderDetailViewModel,
                toastHelper = toastHelper
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
    inOrderList: List<ServiceOrderModel>,
    navController: NavController,
    homeSharedViewModel: HomeSharedViewModel,
    modifier: Modifier = Modifier,
    navigationHelper: NavigationHelper,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    toastHelper: ToastHelper
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
            DashboardGridWithImages(
                pendingCarePlanCount = todayOrderList.count { it.isPendingCare() },
                navController = navController
            )
        }
        // Tab布局显示订单
        item {
            OrderTabLayout(
                todayOrderList = todayOrderList,
                inOrderList = inOrderList,
                navController = navController,
                homeSharedViewModel = homeSharedViewModel,
                navigationHelper = navigationHelper,
                sharedOrderDetailViewModel = sharedOrderDetailViewModel,
                toastHelper = toastHelper
            )
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
 * @param navController 导航控制器
 */
@Composable
fun DashboardGridWithImages(
    pendingCarePlanCount: Int,
    navController: NavController
) {
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
                onClick = { navController.navigateToCarePlansList() }
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.main_ic_records,
                title = "已服务记录",
                subtitle = "查看过往服务记录",
                onClick = { navController.navigateToServiceRecordsList() }
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
    onClick: (() -> Unit)? = null,
) {
    Card(
        onClick = { onClick?.invoke() },
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
                if (subtitle.isNotBlank()) {
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
@Composable
fun InOrderServiceItem(
    order: ServiceOrderModel, 
    onClick: () -> Unit = { }
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.name, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 服务中状态标签
                    Surface(
                        shape = RoundedCornerShape(4.dp), 
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = "工时: ${order.planTotalTime}",
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "地址: ${order.liveAddress}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "进入详情",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun OrderTabLayout(
    todayOrderList: List<TodayServiceOrderModel>,
    inOrderList: List<ServiceOrderModel>,
    navController: NavController,
    homeSharedViewModel: HomeSharedViewModel,
    navigationHelper: NavigationHelper,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    toastHelper: ToastHelper
) {
    val selectedTabIndex by homeSharedViewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val tabs = listOf("待护理计划", "服务中")
    val coroutineScope = rememberCoroutineScope()
    
    Column {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {},
            indicator = { }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { homeSharedViewModel.updateSelectedTabIndex(index) },
                    text = {
                        CustomTabItem(
                            text = title,
                            isSelected = selectedTabIndex == index
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (selectedTabIndex) {
            0 -> {
                val pendingOrders = todayOrderList.filter { it.isPendingCare() }
                if (pendingOrders.isNotEmpty()) {
                    pendingOrders.forEach { order ->
                        ServiceOrderItem(order = order) {
                            if (order.isPendingCare()) {
                                navController.navigateToNursingExecution(order.orderId)
                            } else {
                                navController.navigateToService(order.orderId)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Text(
                        text = "暂无待护理计划",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            1 -> {
                if (inOrderList.isNotEmpty()) {
                    inOrderList.forEach { order ->
                        InOrderServiceItem(order = order) {
                            // 先尝试从缓存获取projectList
                            val cachedOrderInfo = sharedOrderDetailViewModel.getCachedOrderInfo(order.orderId)
                            if (cachedOrderInfo != null) {
                                // 缓存存在，直接跳转
                                val projectList = cachedOrderInfo.projectList
                                navigationHelper.navigateToServiceCountdownWithLogic(
                                    navController = navController,
                                    orderId = order.orderId,
                                    projectList = projectList
                                )
                            } else {
                                 // 缓存不存在，先查询订单详情
                                 coroutineScope.launch {
                                     try {
                                         // 启动异步获取订单详情
                                         sharedOrderDetailViewModel.getOrderInfo(order.orderId)
                                         
                                         // 等待获取完成，使用first来避免持续监听
                                         val finalState = sharedOrderDetailViewModel.uiState
                                             .first { state ->
                                                 state is OrderDetailUiState.Success || state is OrderDetailUiState.Error
                                             }
                                         
                                         when (finalState) {
                                             is OrderDetailUiState.Success -> {
                                                 // 获取成功，直接使用返回的数据跳转
                                                 val projectList = finalState.orderInfo.projectList
                                                 navigationHelper.navigateToServiceCountdownWithLogic(
                                                     navController = navController,
                                                     orderId = order.orderId,
                                                     projectList = projectList
                                                 )
                                             }
                                             is OrderDetailUiState.Error -> {
                                                 // 获取失败，显示错误信息
                                                 toastHelper.showShort("获取订单详情失败：${finalState.message}")
                                                 logE("获取订单详情失败: orderId=${order.orderId}, error=${finalState.message}")
                                             }
                                             else -> {
                                                 // 理论上不会到达这里，因为first已经过滤了状态
                                                 toastHelper.showShort("获取订单详情失败，请稍后重试")
                                                 logE("获取订单详情失败: orderId=${order.orderId}, 未知状态")
                                             }
                                         }
                                     } catch (e: Exception) {
                                         // 网络请求失败或其他异常，添加错误处理
                                         toastHelper.showShort("网络连接异常，请检查网络后重试")
                                         logE("跳转到服务倒计时页面失败: orderId=${order.orderId}", throwable = e)
                                     }
                                 }
                             }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Text(
                        text = "暂无服务中订单",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomTabItem(
    text: String,
    isSelected: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF007AFF) else Color(0xFF999999),
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 自定义指示器，宽度与文字宽度一致
        if (isSelected) {
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            val textWidthDp = with(density) { textLayoutResult.size.width.toDp() }
            
            Box(
                modifier = Modifier
                    .width(textWidthDp)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                IndicatorGradientStart,
                                IndicatorGradientEnd
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        } else {
            // 占位符，保持布局一致
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(2.dp)
            )
        }
    }
}