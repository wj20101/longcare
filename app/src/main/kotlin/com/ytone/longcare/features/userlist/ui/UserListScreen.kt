package com.ytone.longcare.features.userlist.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.api.response.UserInfoModel
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.UnifiedBackHandler
import com.ytone.longcare.features.userlist.vm.UserListViewModel
import com.ytone.longcare.navigation.navigateToUserServiceRecord
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush

/**
 * 用户列表类型枚举
 */
enum class UserListType {
    HAVE_SERVICE, // 已服务工时
    NO_SERVICE,   // 未服务工时
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    navController: NavController,
    userListType: UserListType,
    viewModel: UserListViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val userList by viewModel.userListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // 根据类型设置标题
    val title = when (userListType) {
        UserListType.HAVE_SERVICE -> "已服务工时"
        UserListType.NO_SERVICE -> "未服务工时"
    }

    // 统一处理系统返回键
    UnifiedBackHandler(navController = navController)

    // 初始化时加载数据
    LaunchedEffect(userListType) {
        when (userListType) {
            UserListType.HAVE_SERVICE -> viewModel.getHaveServiceUserList()
            UserListType.NO_SERVICE -> viewModel.getNoServiceUserList()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            UserListContent(
                userList = userList,
                isLoading = isLoading,
                userListType = userListType,
                modifier = Modifier.padding(paddingValues)
            ) { user ->
                // 点击用户项的处理逻辑
                if (userListType == UserListType.HAVE_SERVICE) {
                    // 已服务工时页面，跳转到用户服务记录页面
                    navController.navigateToUserServiceRecord(
                        user.userId.toLong(),
                        user.name,
                        user.address
                    )
                }
                // 其他类型暂时不做处理
            }
        }
    }
}

@Composable
fun UserListContent(
    userList: List<UserInfoModel>,
    isLoading: Boolean,
    userListType: UserListType,
    modifier: Modifier = Modifier,
    onUserClick: (UserInfoModel) -> Unit
) {
    val contentModifier = modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        .background(Color.White)

    if (isLoading) {
        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (userList.isEmpty()) {
        // 空状态视图
        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂无用户数据",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前没有符合条件的用户",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    } else {
        Column(
            modifier = contentModifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(userList) { index, user ->
                    UserListItem(
                        modifier = Modifier.clickable { onUserClick(user) },
                        user = user,
                        userListType = userListType
                    )
                    if (index < userList.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = Color(0xFFF0F0F0)
                        )
                    }
                    if (index == userList.lastIndex) {
                        // 底部留出一些空间
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun UserListItem(
    modifier: Modifier = Modifier,
    user: UserInfoModel,
    userListType: UserListType = UserListType.HAVE_SERVICE
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                val serviceTimeText = when (userListType) {
                    UserListType.HAVE_SERVICE -> "本月已服务工时: ${user.monthServiceTime}"
                    UserListType.NO_SERVICE -> "本月未服务工时: ${user.monthNoServiceTime}"
                }
                Text(
                    text = serviceTimeText,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "地址: ${user.address}",
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.common_details),
            tint = Color.LightGray
        )
    }
}

// --- 预览 ---
@Preview
@Composable
fun UserListItemPreview() {
    val user = UserInfoModel(
        userId = 1,
        name = "张三",
        identityCardNumber = "3301...01",
        age = 78,
        gender = "男",
        address = "杭州市西湖区328弄24号",
        lastServiceTime = "2025-05-20 10:00:00",
        monthServiceTime = 8,
        monthNoServiceTime = 12
    )
    LongCareTheme {
        Surface {
            UserListItem(user = user)
        }
    }
}

@Preview
@Composable
fun UserListContentPreview() {
    val users = listOf(
        UserInfoModel(
            userId = 1,
            name = "孙天成",
            identityCardNumber = "3301...01",
            age = 78,
            gender = "男",
            address = "杭州市西湖区328弄24号",
            lastServiceTime = "2025-05-20 10:00:00",
            monthServiceTime = 8,
            monthNoServiceTime = 12
        ),
        UserInfoModel(
            userId = 2,
            name = "王东明",
            identityCardNumber = "3301...02",
            age = 75,
            gender = "男",
            address = "杭州市西湖区328弄24号",
            lastServiceTime = "2025-05-21 14:00:00",
            monthServiceTime = 8,
            monthNoServiceTime = 10
        )
    )
    LongCareTheme {
        UserListContent(
            userList = users,
            isLoading = false,
            userListType = UserListType.HAVE_SERVICE,
            onUserClick = {}
        )
    }
}

@Preview
@Composable
fun UserListContentEmptyPreview() {
    LongCareTheme {
        UserListContent(
            userList = emptyList(),
            isLoading = false,
            userListType = UserListType.HAVE_SERVICE,
            onUserClick = {}
        )
    }
}

@Preview
@Composable
fun UserListContentLoadingPreview() {
    LongCareTheme {
        UserListContent(
            userList = emptyList(),
            isLoading = true,
            userListType = UserListType.HAVE_SERVICE,
            onUserClick = {}
        )
    }
}