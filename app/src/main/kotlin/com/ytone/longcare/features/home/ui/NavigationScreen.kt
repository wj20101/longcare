package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.theme.BottomNavBackground
import com.ytone.longcare.theme.BottomNavSelectedText
import com.ytone.longcare.theme.BottomNavUnselectedText
import com.ytone.longcare.theme.IndicatorGradientEnd
import com.ytone.longcare.theme.IndicatorGradientStart

@Composable
fun AdaptiveAppNavigationScaffold(
    items: List<AppNavigationItem>,
    selectedItemIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val itemColors =
        NavigationSuiteDefaults.itemColors(
            navigationBarItemColors =
                NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        )

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            items.forEachIndexed { index, item ->
                val isSelected = selectedItemIndex == index
                item(
                    selected = isSelected,
                    onClick = { onItemSelected(index) },
                    modifier = item.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                    icon = {
                        CustomNavItem(
                            text = item.text,
                            isSelected = isSelected,
                        )
                    },
                    colors = itemColors,
                )
            }
        },
        navigationSuiteColors =
            NavigationSuiteDefaults.colors(
                navigationBarContainerColor = BottomNavBackground,
                navigationRailContainerColor = BottomNavBackground,
            ),
        containerColor = Color.Transparent,
        content = content,
    )
}

@PreviewScreenSizes
@Composable
fun AdaptiveAppNavigationPreview() {
    val navigationItems = listOf(
        AppNavigationItem("首页"),
        AppNavigationItem("护理工作"),
        AppNavigationItem("我的")
    )
    AdaptiveAppNavigationScaffold(
        items = navigationItems,
        selectedItemIndex = 1,
        onItemSelected = {},
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

data class AppNavigationItem(
    val text: String,
    val testTag: String? = null,
)

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
