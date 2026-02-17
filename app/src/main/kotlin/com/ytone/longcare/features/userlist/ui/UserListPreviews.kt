package com.ytone.longcare.features.userlist.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.theme.LongCareTheme

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
