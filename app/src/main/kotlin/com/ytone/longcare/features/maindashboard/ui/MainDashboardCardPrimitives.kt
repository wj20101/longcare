package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class InfoCardLayoutSpec(
    val titleFontSize: TextUnit,
    val showSubtitle: Boolean,
    val iconSize: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val badgeEndInset: Dp
)

private object InfoCardLayoutResolverDefaults {
    val CompactThreshold = 160.dp
    val MediumThreshold = 172.dp

    val CompactTitleFontSize = 13.sp
    val MediumTitleFontSize = 14.sp
    val RegularTitleFontSize = 15.sp

    val CompactIconSize = 28.dp
    val MediumIconSize = 30.dp
    val RegularIconSize = 32.dp

    val CompactHorizontalPadding = 8.dp
    val MediumHorizontalPadding = 9.dp
    val RegularHorizontalPadding = 10.dp

    val VerticalPadding = 8.dp
    val CompactBadgeEndInset = 18.dp
    val RegularBadgeEndInset = 20.dp
}

internal fun resolveInfoCardLayoutSpec(cardWidth: Dp, hasBadge: Boolean): InfoCardLayoutSpec {
    return when {
        cardWidth <= InfoCardLayoutResolverDefaults.CompactThreshold -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.CompactTitleFontSize,
            showSubtitle = false,
            iconSize = InfoCardLayoutResolverDefaults.CompactIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.CompactHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.CompactBadgeEndInset else 0.dp
        )
        cardWidth <= InfoCardLayoutResolverDefaults.MediumThreshold -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.MediumTitleFontSize,
            showSubtitle = false,
            iconSize = InfoCardLayoutResolverDefaults.MediumIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.MediumHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.CompactBadgeEndInset else 0.dp
        )
        else -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.RegularTitleFontSize,
            showSubtitle = true,
            iconSize = InfoCardLayoutResolverDefaults.RegularIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.RegularHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.RegularBadgeEndInset else 0.dp
        )
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    iconContentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    val hasBadge = badgeCount != null && badgeCount > 0
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val spec = resolveInfoCardLayoutSpec(cardWidth = maxWidth, hasBadge = hasBadge)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = spec.horizontalPadding,
                        vertical = spec.verticalPadding
                    )
            ) {
                if (hasBadge) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = Color(0xFFFFC107)
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = spec.badgeEndInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(spec.iconSize)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = spec.titleFontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (spec.showSubtitle && subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                lineHeight = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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
            }
        }
    }
}

@Composable
fun ImageWithAdaptiveWidth(
    @DrawableRes drawableResId: Int,
    fixedHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val painter = painterResource(id = drawableResId)

    val aspectRatio = if (painter.intrinsicSize.height > 0) {
        painter.intrinsicSize.width / painter.intrinsicSize.height
    } else {
        1.0f
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillHeight,
        modifier = modifier
            .height(fixedHeight)
            .aspectRatio(aspectRatio)
    )
}
