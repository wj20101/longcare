package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ytone.longcare.R

// 定义 Banner 数据项
data class BannerItem(
    val id: Int,
    @DrawableRes val imageRes: Int, // 如果是网络图片
    val description: String = "Banner Image $id"
)

@Composable
fun ImageBannerPager(
    modifier: Modifier = Modifier,
    bannerItems: List<BannerItem>,
    autoScrollDurationMillis: Long = 3000L, // 自动滑动间隔
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    indicatorInactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    indicatorSize: Dp = 8.dp,
    indicatorSpacing: Dp = 8.dp
) {
    if (bannerItems.isEmpty()) {
        // 如果没有 Banner 数据，可以显示一个占位符或什么都不显示
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray)
        ) { /* Empty Banner Placeholder */ }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0, // 可以设置初始页，例如 banners.size * 500
        initialPageOffsetFraction = 0f
    ) {
        bannerItems.size // 总页数
    }

    var isUserTouching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 自动滑动逻辑
    LaunchedEffect(key1 = pagerState.currentPage, key2 = isUserTouching, key3 = bannerItems.size) {
        if (bannerItems.size > 1 && !isUserTouching) {
            delay(autoScrollDurationMillis)
            val nextPage = (pagerState.currentPage + 1) % bannerItems.size
            coroutineScope.launch { // 使用新的 coroutineScope 来避免 LaunchedEffect 重启取消动画
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // 可以去掉阴影
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { // 监听触摸事件
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false) // 手指按下
                            isUserTouching = true
                            // 等待所有指针抬起或手势取消
                            do {
                                val event = awaitPointerEvent()
                                // 如果需要，可以在这里处理其他事件
                            } while (event.changes.any { it.pressed })
                            isUserTouching = false // 手指抬起
                        }
                    }
            ) { pageIndex ->
                val item = bannerItems[pageIndex]
                Image(
                    painter = rememberAsyncImagePainter(model = item.imageRes),
                    contentDescription = item.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 指示器
        if (bannerItems.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(indicatorSpacing)
            ) {
                bannerItems.indices.forEach { index ->
                    BannerIndicatorDot(
                        isSelected = index == pagerState.currentPage,
                        color = if (index == pagerState.currentPage) indicatorColor else indicatorInactiveColor,
                        size = indicatorSize
                    )
                }
            }
        }
    }
}

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

// --- 预览 ---
@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun ImageBannerPagerPreview() {
    val sampleBanners = listOf(
        BannerItem(1, R.drawable.main_banner, "Banner 1"),
        BannerItem(2, R.drawable.main_banner, "Banner 2"),
        BannerItem(3, R.drawable.main_banner, "Banner 3"),
    )
    MaterialTheme {
        Surface {
            ImageBannerPager(
                bannerItems = sampleBanners,
                modifier = Modifier.height(200.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun SingleImageBannerPagerPreview() {
    val singleBanner = listOf(
        BannerItem(1, R.drawable.main_banner, "Single Banner")
    )
    MaterialTheme {
        Surface {
            ImageBannerPager(
                bannerItems = singleBanner,
                modifier = Modifier.height(200.dp)
            )
        }
    }
}