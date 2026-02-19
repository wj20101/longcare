package com.ytone.longcare.features.selectservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CenterStateText(
    text: String = "",
    showProgress: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
internal fun SelectServiceBottomActions(
    modifier: Modifier = Modifier,
    selectServiceType: Int,
    serviceItems: SnapshotStateList<ServiceItem>,
    isStarOrderLoading: Boolean,
    onToggleSelectAll: () -> Unit,
    onNextStep: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFF6F9FF).copy(alpha = 0.9f),
                        Color(0xFFF6F9FF)
                    ),
                    startY = 0f,
                    endY = 100f
                )
            )
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectServiceType == 0) {
                SelectAllButton(
                    isAllSelected = serviceItems.isNotEmpty() && serviceItems.all { it.isSelected },
                    enabled = !isStarOrderLoading,
                    onClick = onToggleSelectAll,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            NextStepButton(
                text = if (!isStarOrderLoading) "开始服务" else "正在处理...",
                enabled = serviceItems.any { it.isSelected } && !isStarOrderLoading,
                onClick = onNextStep,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
