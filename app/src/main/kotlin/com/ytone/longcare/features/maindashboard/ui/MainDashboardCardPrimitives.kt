package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier.height(65.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = iconContentDescription,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeCount != null && badgeCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(containerColor = Color(0xFFFFC107)) {
                            Text(
                                text = badgeCount.toString(),
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 12.sp,
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
