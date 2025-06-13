package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.theme.BottomNavBackground
import com.ytone.longcare.theme.BottomNavSelectedText
import com.ytone.longcare.theme.BottomNavUnselectedText
import com.ytone.longcare.theme.IndicatorGradientEnd
import com.ytone.longcare.theme.IndicatorGradientStart


@Composable
fun AppBottomNavigation(
    items: List<CustomBottomNavigationItem>, selectedItemIndex: Int, onItemSelected: (Int) -> Unit
) {
    CustomBottomNavigationBar(
        items = items, selectedItemIndex = selectedItemIndex, onItemSelected = onItemSelected
    )
}

@Preview
@Composable
fun AppBottomNavigationPreview() {
    val bottomNavItems = listOf(
        CustomBottomNavigationItem("首页"),
        CustomBottomNavigationItem("护理工作"),
        CustomBottomNavigationItem("我的")
    )
    CustomBottomNavigationBar(items = bottomNavItems, selectedItemIndex = 1, onItemSelected = {})
}

// 定义底部导航项的数据结构
data class CustomBottomNavigationItem(
    val text: String,
)

@Composable
fun CustomBottomNavigationBar(
    modifier: Modifier = Modifier,
    items: List<CustomBottomNavigationItem>,
    selectedItemIndex: Int,
    onItemSelected: (Int) -> Unit,
    topCornerRadius: Int = 16 // 顶部圆角大小，可以根据需要调整
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = topCornerRadius.dp, topEnd = topCornerRadius.dp)),
        containerColor = BottomNavBackground, // 使用在Color.kt中定义的背景色
        tonalElevation = 4.dp // 可以根据需要调整阴影效果
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedItemIndex == index
            NavigationBarItem(
                // 当前项是否被选中
                selected = isSelected,
                // 点击事件：更新选中的索引
                onClick = { onItemSelected(index) },
                // 关键点：使用 icon 参数来放置我们的自定义Composable
                icon = {
                    CustomNavItem(
                        text = item.text, isSelected = isSelected
                    )
                },
                // 关键点：通过设置颜色来移除默认的指示器背景
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

/**
 * 单个导航项的自定义UI
 *
 * @param text 显示的文字
 * @param isSelected 是否被选中
 */
@Composable
private fun CustomNavItem(text: String, isSelected: Boolean) {
    // 根据是否选中来决定文字颜色
    val textColor = if (isSelected) BottomNavSelectedText else BottomNavUnselectedText

    // 使用Column来垂直排列文字和指示器
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 8.dp) // 给整体一些垂直内边距，让点击区域更大
    ) {
        // 文字部分
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )

        // 文字和指示器之间的间距
        Spacer(modifier = Modifier.height(2.dp))

        // 指示器部分
        if (isSelected) {
            // 定义渐变色
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(
                    IndicatorGradientStart, IndicatorGradientEnd
                )
            )
            // 使用Box绘制圆角渐变线条
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(4.dp)
                    .background(
                        brush = gradientBrush, shape = RoundedCornerShape(50) // 50%的圆角，形成胶囊形状
                    )
            )
        } else {
            // 未选中时，放置一个等高的透明Box来占位，防止切换时UI跳动
            Box(modifier = Modifier.height(4.dp))
        }
    }
}