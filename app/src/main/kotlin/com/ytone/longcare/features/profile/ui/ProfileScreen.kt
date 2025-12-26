package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.R
import com.ytone.longcare.api.response.NurseServiceTimeModel
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.profile.vm.ProfileViewModel
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.models.protos.User
import com.ytone.longcare.navigation.HomeRoute
import com.ytone.longcare.navigation.navigateToHaveServiceUserList
import com.ytone.longcare.navigation.navigateToNoServiceUserList
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.components.UserAvatar

// 主屏幕入口
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController, viewModel: ProfileViewModel = hiltViewModel()
) {
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(HomeRoute)
    }
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel(parentEntry)
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    val statsState by viewModel.statsState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // 当此 Composable 的生命周期进入 RESUMED 状态时（即回到此页面），
        // 就会执行刷新操作。
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshStats()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "我的", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            user?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LogoutButton(onClick = { viewModel.logout() })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "版本号: ${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
                            color = Color.Black.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        user?.let { loggedInUser ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                UserInfoSection(user = loggedInUser)
                Spacer(modifier = Modifier.height(24.dp))
                StatsCard(navController = navController, stats = statsState)
                Spacer(modifier = Modifier.height(24.dp))
                OptionsCard()
                Spacer(modifier = Modifier.height(24.dp))
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun UserInfoSection(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl = user.headUrl)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = user.userName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = user.userIdentityShow(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
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

@Composable
fun StatsCard(navController: NavController, stats: NurseServiceTimeModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                value = stats.haveServiceTime.toString(),
                label = "已服务工时",
                onClick = { navController.navigateToHaveServiceUserList() }
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp), 
                thickness = 1.dp, 
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                value = stats.haveServiceNum.toString(),
                label = "服务次数",
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp), 
                thickness = 1.dp, 
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                value = stats.noServiceTime.toString(),
                label = "未服务工时",
                onClick = { navController.navigateToNoServiceUserList() }
            )
        }
    }
}

@Composable
fun StatItem(
    modifier: Modifier = Modifier,
    value: String, 
    label: String, 
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable{ onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = value, 
            fontWeight = FontWeight.Bold, 
            fontSize = 22.sp,
            color = if (onClick != null) Color(0xFF333333) else Color(0xFF666666)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label, 
            color = if (onClick != null) Color(0xFF666666) else Color.Gray, 
            fontSize = 12.sp
        )
    }
}

@Composable
fun OptionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            OptionItem(icon = Icons.Default.Description, text = "信息上报", onClick = {})
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F0)
            )
            OptionItem(icon = Icons.Default.Person, text = "个人信息", onClick = {})
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F0F0)
            )
            OptionItem(icon = Icons.Default.Settings, text = "设置", onClick = {})
        }
    }
}

@Composable
fun OptionItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.common_arrow_right),
            tint = Color.LightGray
        )
    }
}

@Composable
fun LogoutButton(onClick: () -> Unit = {}) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White, contentColor = Color.Red
        )
    ) {
        Text(text = "退出登录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
fun UserInfoSectionPreview() {
    val user = User(
        companyId = 1,
        accountId = 1,
        userId = 1,
        userName = "张三",
        headUrl = "https://example.com/avatar.jpg",
        userIdentity = 1,
        identityCardNumber = "123456789012345678",
        gender = 1,
        token = "test_token"
    )
    LongCareTheme {
        Surface {
            UserInfoSection(user = user)
        }
    }
}

@Preview
@Composable
fun StatsCardPreview() {
    val stats = NurseServiceTimeModel(
        haveServiceTime = 100,
        haveServiceNum = 10,
        noServiceTime = 20
    )
    LongCareTheme {
        Surface {
            StatsCard(navController = rememberNavController(), stats = stats)
        }
    }
}

@Preview
@Composable
fun StatItemPreview() {
    LongCareTheme {
        Surface {
            StatItem(value = "120", label = "已服务工时")
        }
    }
}

@Preview
@Composable
fun OptionsCardPreview() {
    LongCareTheme {
        Surface {
            OptionsCard()
        }
    }
}

@Preview
@Composable
fun OptionItemPreview() {
    LongCareTheme {
        Surface {
            OptionItem(
                icon = Icons.Default.Description,
                text = "信息上报",
                onClick = {}
            )
        }
    }
}

@Preview
@Composable
fun LogoutButtonPreview() {
    LongCareTheme {
        Surface {
            LogoutButton(onClick = {})
        }
    }
}
