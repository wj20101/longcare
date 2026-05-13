package com.ytone.longcare.presentation.countdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.theme.LongCareTheme

@Preview(
    name = "默认",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun CountdownAlarmScreenPreview() {
    LongCareTheme {
        CountdownAlarmScreen(
            orderId = "202605131234",
            serviceName = "居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务",
            onDismiss = {}
        )
    }
}

@Preview(
    name = "低分辨率小屏",
    showBackground = true,
    widthDp = 320,
    heightDp = 480
)
@Composable
private fun CountdownAlarmScreenSmallPreview() {
    LongCareTheme {
        CountdownAlarmScreen(
            orderId = "202605131234",
            serviceName = "居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务",
            onDismiss = {}
        )
    }
}

@Preview(
    name = "长服务名称",
    showBackground = true,
    widthDp = 320,
    heightDp = 480
)
@Composable
private fun CountdownAlarmScreenLongNamePreview() {
    LongCareTheme {
        CountdownAlarmScreen(
            orderId = "202605139999888877776666",
            serviceName = "居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务（含助浴助洁）居家养老上门护理康复训练服务",
            onDismiss = {}
        )
    }
}
