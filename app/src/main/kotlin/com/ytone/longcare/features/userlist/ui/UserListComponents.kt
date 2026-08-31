package com.ytone.longcare.features.userlist.ui

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.core.ui.R as CoreUiR

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
        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.user_list_empty),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.user_list_empty_description),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    } else {
        Column(modifier = contentModifier) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(userList) { index, user ->
                    UserListItem(
                        modifier = Modifier.clickable(onClick = singleClick { onUserClick(user) }),
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
                    UserListType.HAVE_SERVICE -> stringResource(
                        R.string.user_list_month_served_hours,
                        user.monthServiceTime,
                    )
                    UserListType.NO_SERVICE -> stringResource(
                        R.string.user_list_month_unserved_hours,
                        user.monthNoServiceTime,
                    )
                }
                Text(
                    text = serviceTimeText,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(CoreUiR.string.common_address, user.address),
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(CoreUiR.string.common_details),
            tint = Color.LightGray
        )
    }
}
