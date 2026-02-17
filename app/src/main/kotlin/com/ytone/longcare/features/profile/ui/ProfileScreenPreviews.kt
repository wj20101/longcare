package com.ytone.longcare.features.profile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.User
import com.ytone.longcare.theme.LongCareTheme

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
            StatsCard(
                actions = ProfileActions(
                    onNavigateToHaveServiceUserList = {},
                    onNavigateToNoServiceUserList = {}
                ),
                stats = stats
            )
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
