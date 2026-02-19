package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

fun PagerState.getOffsetFractionForPage(page: Int): Float {
    return ((currentPage - page) + currentPageOffsetFraction).coerceIn(-1f, 1f)
}

@Composable
fun BannerIndicatorDot(
    isSelected: Boolean,
    color: Color,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
