package com.ytone.longcare.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R

enum class TagCategory(val textColor: Color, val tintColors: List<Color>? = null) {
    DEFAULT(textColor = Color(0xFF134AA8)),
    ORANGE(
        textColor = Color(0xFFCA541F),
        tintColors = listOf(Color(0xFFFFB999), Color(0xFFE7C06B))
    ),
    BLUE(textColor = Color(0xFF154EA8), tintColors = listOf(Color(0xFF70B7FF), Color(0xFF7CE4FF)))
}

@Composable
fun ServiceHoursTag(
    modifier: Modifier = Modifier,
    tagText: String,
    tagCategory: TagCategory = TagCategory.DEFAULT
) {
    ServiceHoursTag(
        modifier = modifier,
        tagText = tagText,
        tagTextColor = tagCategory.textColor,
        tintColors = tagCategory.tintColors
    )
}



@Composable
fun ServiceHoursTag(
    modifier: Modifier = Modifier,
    tagText: String,
    tagTextColor: Color,
    tintColors: List<Color>? = null
) {
    Box(
        modifier = modifier
    ) {
        val gradientBrush =
            if (!tintColors.isNullOrEmpty()) Brush.horizontalGradient(colors = tintColors) else null
        var imageModifier = Modifier.size(120.dp, 44.dp)
        if (gradientBrush != null) {
            imageModifier = imageModifier
                .graphicsLayer(alpha = 0.99f)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = gradientBrush, blendMode = BlendMode.SrcIn
                    )
                }
        }
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.service_tab_bg),
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.FillBounds
        )

        // 文字内容
        Text(
            text = tagText,
            color = tagTextColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.Center) // 文字在Box中垂直居中，水平靠左
                .offset(y = -(6.dp))
        )
    }
}