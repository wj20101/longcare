package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R

@Composable
internal fun NursingDateTabsRow(
    dateList: List<UiDate>,
    pagerState: PagerState,
    selectedTabContentColor: Color,
    unselectedTabContentColor: Color,
    onTabClick: (Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(pagerState.currentPage) {
        val targetIndex = pagerState.currentPage
        if (targetIndex >= 0 && targetIndex < dateList.size) {
            val itemWidthPx = with(density) { 65.dp.toPx() }
            val centerOffsetPx = (itemWidthPx * 2).toInt()
            lazyListState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -centerOffsetPx
            )
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(items = dateList, key = { index, _ -> index }) { index, dateInfo ->
            val isSelected = pagerState.currentPage == index
            NursingDateTabItem(
                dateInfo = dateInfo,
                isSelected = isSelected,
                selectedTabContentColor = selectedTabContentColor,
                unselectedTabContentColor = unselectedTabContentColor,
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun NursingDateTabItem(
    dateInfo: UiDate,
    isSelected: Boolean,
    selectedTabContentColor: Color,
    unselectedTabContentColor: Color,
    onClick: () -> Unit
) {
    val tabTextColor = if (isSelected) selectedTabContentColor else unselectedTabContentColor

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .then(if (isSelected) Modifier.background(Color.White) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(dateInfo.displayInfo.dayOfWeek.labelRes),
                fontWeight = FontWeight.Bold,
                color = tabTextColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateInfo.displayInfo.dateLabel,
                fontSize = 12.sp,
                color = tabTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NursingTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(R.string.home_navigation_nursing),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White
        )
    )
}
