package com.ytone.longcare.features.userlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
import com.ytone.longcare.features.userlist.api.UserListActions
import com.ytone.longcare.features.userlist.vm.UserListViewModel
import com.ytone.longcare.theme.bgGradientBrush

/**
 * 用户列表类型枚举
 */
enum class UserListType {
    HAVE_SERVICE,
    NO_SERVICE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    actions: UserListActions,
    userListType: UserListType,
    viewModel: UserListViewModel = hiltViewModel()
) {

    val userList by viewModel.userListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val uiMessages by viewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UiMessageSnackbarEffect(
        messages = uiMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeUiMessage,
    )

    val title = when (userListType) {
        UserListType.HAVE_SERVICE -> "已服务工时"
        UserListType.NO_SERVICE -> "未服务工时"
    }

    CustomBackHandler(customAction = actions.onNavigateBack)

    LaunchedEffect(userListType) {
        when (userListType) {
            UserListType.HAVE_SERVICE -> viewModel.getHaveServiceUserList()
            UserListType.NO_SERVICE -> viewModel.getNoServiceUserList()
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = singleClick { actions.onNavigateBack() }) {
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
                    )
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
                if (userListType == UserListType.HAVE_SERVICE) {
                    actions.onNavigateToUserServiceRecord(
                        user.userId.toLong(),
                        user.name,
                        user.address
                    )
                }
            }
        }
    }
}
