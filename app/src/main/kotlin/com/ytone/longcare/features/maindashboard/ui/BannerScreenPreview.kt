package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R

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
