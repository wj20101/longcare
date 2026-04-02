package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.ui.components.BottomSafeActionContainer

@Composable
internal fun BoxScope.ServiceCountdownBottomActionBar(
    countdownState: ServiceCountdownState,
    onActionClick: () -> Unit
) {
    BottomSafeActionContainer(
        modifier = Modifier.align(Alignment.BottomCenter),
        horizontalPadding = 16.dp,
        topPadding = 0.dp,
        extraBottomPadding = 16.dp,
        gradientBackground = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFF6F9FF).copy(alpha = 0.9f),
                Color(0xFFF6F9FF)
            ),
            startY = 0f,
            endY = 100f
        )
    ) {
        Button(
            onClick = singleClick(onClick = onActionClick),
            enabled = countdownState != ServiceCountdownState.ENDED,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = countdownActionColor(countdownState))
        ) {
            Text(
                text = countdownActionText(countdownState),
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}

private fun countdownActionColor(state: ServiceCountdownState): Color {
    return when (state) {
        ServiceCountdownState.RUNNING -> Color(0xFFFF9500)
        ServiceCountdownState.COMPLETED, ServiceCountdownState.OVERTIME -> Color(0xFF4A90E2)
        ServiceCountdownState.ENDED -> Color.Gray
    }
}

private fun countdownActionText(state: ServiceCountdownState): String {
    return when (state) {
        ServiceCountdownState.RUNNING -> "提前结束服务"
        ServiceCountdownState.COMPLETED, ServiceCountdownState.OVERTIME -> "结束服务"
        ServiceCountdownState.ENDED -> "服务已结束"
    }
}
