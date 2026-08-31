package com.ytone.longcare.core.ui.header

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.core.ui.text.labelRes
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.userIdentityDisplay
import com.ytone.longcare.ui.components.UserAvatar

@Composable
fun TopHeader(user: CurrentUser, companyName: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val layoutSpec = resolveTopHeaderLayoutSpec(maxWidth, LocalDensity.current.fontScale)
        if (layoutSpec.useCompactLargeTextLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderLogo()
                    Spacer(modifier = Modifier.weight(1f))
                    HeaderUserInfo(
                        user = user,
                        modifier = Modifier.widthIn(min = 72.dp, max = 140.dp),
                        userNameMaxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HeaderAvatar(user)
                }
                HeaderCompanyName(companyName = companyName, maxLines = 2)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    HeaderLogo()
                    HeaderCompanyName(companyName = companyName, maxLines = 3)
                }
                HeaderUserInfo(
                    user = user,
                    modifier = Modifier.width(layoutSpec.regularUserBlockWidth),
                    userNameMaxLines = 1,
                )
                HeaderAvatar(user)
            }
        }
    }
}

internal data class TopHeaderLayoutSpec(
    val useCompactLargeTextLayout: Boolean,
    val regularUserBlockWidth: Dp,
)

internal fun resolveTopHeaderLayoutSpec(
    availableWidth: Dp,
    fontScale: Float,
): TopHeaderLayoutSpec = TopHeaderLayoutSpec(
    useCompactLargeTextLayout = availableWidth < 340.dp && fontScale >= 1.3f,
    regularUserBlockWidth = if (availableWidth >= 480.dp) 160.dp else 120.dp,
)

@Composable
private fun HeaderLogo() {
    val painter = painterResource(R.drawable.app_logo_small_white)
    val aspectRatio = if (painter.intrinsicSize.height > 0) {
        painter.intrinsicSize.width / painter.intrinsicSize.height
    } else {
        1f
    }
    Image(
        painter = painter,
        contentDescription = stringResource(R.string.main_dashboard_logo),
        contentScale = ContentScale.FillHeight,
        modifier = Modifier.height(34.dp).aspectRatio(aspectRatio),
    )
}

@Composable
private fun HeaderCompanyName(companyName: String, maxLines: Int) {
    if (companyName.isNotEmpty()) {
        Text(
            text = companyName,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_top_company_name")
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeaderUserInfo(
    user: CurrentUser,
    modifier: Modifier,
    userNameMaxLines: Int,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = user.userName,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 20.sp,
            maxLines = userNameMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("home_top_user_name"),
        )
        Text(
            text = stringResource(user.userIdentityDisplay().labelRes()),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("home_top_user_identity"),
        )
    }
}

@Composable
private fun HeaderAvatar(user: CurrentUser) {
    UserAvatar(
        modifier = Modifier.testTag("home_top_avatar"),
        avatarUrl = user.headUrl,
    )
}
