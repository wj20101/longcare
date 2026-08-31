package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.theme.PrimaryBlue
import com.ytone.longcare.theme.TextColorPrimary
import com.ytone.longcare.theme.TextColorSecondary
import com.ytone.longcare.core.ui.R as CoreUiR

@Composable
internal fun OptionsCard(actions: ProfileActions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_options_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            ProfileOptionRow(
                text = stringResource(CoreUiR.string.profile_user_agreement),
                description = stringResource(R.string.profile_user_agreement_description),
                icon = Icons.Outlined.Description,
                onClick = actions.onOpenUserAgreement,
                modifier = Modifier.testTag("profile_user_agreement_entry")
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 1.dp,
                color = Color(0xFFF0F3F8)
            )
            ProfileOptionRow(
                text = stringResource(CoreUiR.string.profile_privacy_policy),
                description = stringResource(R.string.profile_privacy_policy_description),
                icon = Icons.Outlined.PrivacyTip,
                onClick = actions.onOpenPrivacyPolicy,
                modifier = Modifier.testTag("profile_privacy_policy_entry")
            )
        }
    }
}

@Composable
private fun ProfileOptionRow(
    text: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = PrimaryBlue.copy(alpha = 0.1f),
            contentColor = PrimaryBlue
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = TextColorSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFB8C2D0),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun LogoutButton(onClick: () -> Unit = {}) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Red
        )
    ) {
        Text(
            text = stringResource(R.string.profile_logout),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
