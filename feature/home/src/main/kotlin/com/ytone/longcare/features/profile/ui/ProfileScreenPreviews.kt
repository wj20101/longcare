package com.ytone.longcare.features.profile.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.theme.LongCareTheme

@Preview
@Composable
internal fun UserInfoSectionPreview() {
    val user = CurrentUser(
        scopeKey = UserScopeKey(companyId = 1, accountId = 1, userId = 1),
        userName = "张三",
        headUrl = "https://example.com/avatar.jpg",
        userIdentity = 1,
        gender = 1,
    )
    LongCareTheme {
        Surface {
            UserInfoSection(user = user)
        }
    }
}

@Preview
@Composable
internal fun StatsCardPreview() {
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
                    onNavigateToNoServiceUserList = {},
                    onOpenUserAgreement = {},
                    onOpenPrivacyPolicy = {}
                ),
                stats = stats
            )
        }
    }
}

@Preview
@Composable
internal fun StatItemPreview() {
    LongCareTheme {
        Surface {
            StatItem(value = "120", label = "已服务工时")
        }
    }
}

@Preview
@Composable
internal fun OptionsCardPreview() {
    LongCareTheme {
        Surface {
            OptionsCard(
                actions = ProfileActions(
                    onNavigateToHaveServiceUserList = {},
                    onNavigateToNoServiceUserList = {},
                    onOpenUserAgreement = {},
                    onOpenPrivacyPolicy = {}
                )
            )
        }
    }
}

@Preview
@Composable
internal fun LogoutButtonPreview() {
    LongCareTheme {
        Surface {
            LogoutButton(onClick = {})
        }
    }
}
