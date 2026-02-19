package com.ytone.longcare.features.nursing.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.theme.LongCareTheme

@Preview
@Composable
fun PlanListPreview() {
    val plans = listOf(
        ServiceOrderModel(
            orderId = 1L,
            name = "John Doe",
            planTotalTime = 60,
            liveAddress = "123 Main St",
            state = 0
        ),
        ServiceOrderModel(
            orderId = 2L,
            name = "Jane Smith",
            planTotalTime = 90,
            liveAddress = "456 Oak Ave",
            state = 1
        )
    )
    PlanList(plans = plans, isLoading = false, onGoToDetailClick = {})
}

@Preview
@Composable
fun PlanListEmptyPreview() {
    PlanList(plans = emptyList(), isLoading = false, onGoToDetailClick = {})
}

@Preview
@Composable
fun PlanListWithSingleItemPreview() {
    val plans = listOf(
        ServiceOrderModel(
            orderId = 1L,
            name = "John Doe",
            planTotalTime = 60,
            liveAddress = "123 Main St",
            state = 0
        )
    )
    PlanList(plans = plans, isLoading = false, onGoToDetailClick = {})
}

@Preview(showBackground = true)
@Composable
fun PlanListPreviewEmpty() {
    LongCareTheme {
        PlanList(plans = emptyList(), isLoading = false, onGoToDetailClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PlanListPreviewWithData() {
    val samplePlans = listOf(
        ServiceOrderModel(
            orderId = 1,
            name = "张三",
            planTotalTime = 60,
            liveAddress = "北京市朝阳区 xxx 街道 123 号",
            state = 0
        ),
        ServiceOrderModel(
            orderId = 2,
            name = "李四",
            planTotalTime = 90,
            liveAddress = "上海市浦东新区 yyy 路 456 号",
            state = 1
        ),
        ServiceOrderModel(
            orderId = 3,
            name = "王五",
            planTotalTime = 45,
            liveAddress = "广州市天河区 zzz 大道 789 号",
            state = 2
        )
    )
    LongCareTheme {
        PlanList(plans = samplePlans, isLoading = false, onGoToDetailClick = {})
    }
}
