package com.ytone.longcare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BottomSafeActionContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    topPadding: Dp = 16.dp,
    extraBottomPadding: Dp = 16.dp,
    gradientBackground: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    val decoratedModifier = modifier
        .fillMaxWidth()
        .then(
            if (gradientBackground != null) {
                Modifier.background(gradientBackground)
            } else {
                Modifier
            }
        )
        .padding(horizontal = horizontalPadding)
        .padding(top = topPadding)
        .padding(
            bottom = navigationBarPadding.calculateBottomPadding() + extraBottomPadding
        )

    Box(modifier = decoratedModifier, content = content)
}
