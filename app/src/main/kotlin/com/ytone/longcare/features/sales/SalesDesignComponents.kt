package com.ytone.longcare.features.sales

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.model.UserLatentCheckState
import java.util.Calendar

internal val SalesBlue = Color(0xFF348CF5)
internal val SalesTopBlue = Color(0xFF478CF8)
internal val SalesTextPrimary = Color(0xFF16355F)
internal val SalesTextSecondary = Color(0xFF676B73)
internal val SalesSuccessGreen = Color(0xFF1FC243)
internal val SalesErrorRed = Color(0xFFFF4148)
internal val SalesGradient =
    Brush.verticalGradient(
        0f to SalesTopBlue,
        0.46f to Color(0xFF9CC5FB),
        1f to Color(0xFFF5F8FD),
    )

@Composable
internal fun useSalesLargeTextLayout(): Boolean =
    LocalDensity.current.fontScale >= 1.3f

@Composable
internal fun SalesPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(SalesGradient),
    ) {
        content()
    }
}

@Composable
internal fun SalesTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(50.dp)
                        .then(backButtonModifier),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SalesPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SalesBlue,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFB5C8E3),
                disabledContentColor = Color.White,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun SalesOutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, SalesBlue),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = SalesBlue,
                disabledContentColor = Color(0xFF9CB4D1),
            ),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun SalesStatusBadge(
    connected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val color = if (connected) SalesSuccessGreen else SalesErrorRed
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector =
                    if (connected) {
                        Icons.Rounded.Check
                    } else {
                        Icons.Rounded.Remove
                    },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(29.dp),
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SalesSectionTab(
    text: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 42.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = 18.dp,
                    )
                )
                .background(Brush.horizontalGradient(colors))
                .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = SalesTextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun SalesLoadingOverlay(
    isVisible: Boolean,
    message: String,
) {
    if (!isVisible) return
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = SalesBlue,
                strokeWidth = 3.dp,
            )
            Text(
                text = message.ifBlank { stringResource(R.string.sales_common_wait) },
                color = SalesTextPrimary,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
internal fun SalesFeatureIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier =
            modifier
                .size(42.dp),
    )
}

@Composable
internal fun SalesInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(0xFF171717),
) {
    val labelContent: @Composable () -> Unit = {
        Text(
            text = label,
            color = SalesTextSecondary,
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
    }
    val valueContent: @Composable (Modifier) -> Unit = { valueModifier ->
        Text(
            text = value.ifBlank { "—" },
            modifier = valueModifier,
            color = valueColor,
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
    }
    if (useSalesLargeTextLayout()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labelContent()
            valueContent(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.width(70.dp)) {
                labelContent()
            }
            valueContent(Modifier.weight(1f))
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal fun identityCardAge(identityCardNumber: String): Int? {
    val normalized = identityCardNumber.trim()
    val birthYear =
        when (normalized.length) {
            18 -> normalized.substring(6, 10).toIntOrNull()
            15 -> normalized.substring(6, 8).toIntOrNull()?.plus(1900)
            else -> null
        } ?: return null
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    return (currentYear - birthYear).takeIf { it in 0..120 }
}

@StringRes
internal fun Int.toSalesCheckStateLabelRes(): Int =
    when (this) {
        UserLatentCheckState.ALL -> R.string.sales_check_state_all
        UserLatentCheckState.NOT_SUBMITTED ->
            R.string.sales_check_state_not_submitted

        UserLatentCheckState.PENDING_REVIEW ->
            R.string.sales_check_state_pending_review

        UserLatentCheckState.APPROVED -> R.string.sales_check_state_approved
        UserLatentCheckState.REJECTED -> R.string.sales_check_state_rejected
        else -> R.string.sales_check_state_unknown
    }

@Composable
internal fun Int.toSalesCheckStateLabel(): String =
    stringResource(toSalesCheckStateLabelRes())

internal fun Int.toSalesCheckStateColor(): Color =
    when (this) {
        UserLatentCheckState.NOT_SUBMITTED -> Color(0xFFF09A00)
        UserLatentCheckState.PENDING_REVIEW -> Color(0xFF1688F8)
        UserLatentCheckState.APPROVED -> Color(0xFF20B83D)
        UserLatentCheckState.REJECTED -> Color(0xFFFF4A19)
        else -> SalesTextSecondary
    }

internal fun String.displayDate(): String {
    val value = trim()
    if (value.length >= 10 && value[4] == '-' && value[7] == '-') {
        return value.substring(0, 10)
    }
    return value.ifBlank { "—" }
}

internal fun Modifier.salesWhiteCard(radius: Int = 14): Modifier =
    clip(RoundedCornerShape(radius.dp))
        .background(Color.White)

internal fun Modifier.salesBlueBorder(radius: Int = 28): Modifier =
    border(
        width = 1.dp,
        color = SalesBlue,
        shape = RoundedCornerShape(radius.dp),
    )

@Composable
internal fun SalesSuccessPanel(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 196.dp)
                .salesWhiteCard()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(SalesSuccessGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            color = SalesSuccessGreen,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
