package com.ytone.longcare.features.servicehours.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM

@Preview
@Composable
fun ServiceRecordListPreview() {
    val sampleProjects = listOf(
        ServiceProjectM(
            projectId = 1,
            projectName = "日常清洁",
            serviceTime = 60,
            lastServiceTime = "2023-10-26 10:00"
        ),
        ServiceProjectM(
            projectId = 2,
            projectName = "健康监测",
            serviceTime = 30,
            lastServiceTime = "2023-10-26 11:30"
        ),
        ServiceProjectM(
            projectId = 3,
            projectName = "助浴服务",
            serviceTime = 90,
            lastServiceTime = "2023-10-25 14:00"
        )
    )
    val sampleOrderInfo = ServiceOrderInfoModel(
        startTime = "09:00",
        endTime = "12:00"
    )
    MaterialTheme {
        ServiceRecordList(projects = sampleProjects, orderInfo = sampleOrderInfo)
    }
}

@Preview
@Composable
fun ServiceRecordItemPreview() {
    val sampleProject = ServiceProjectM(
        projectId = 1,
        projectName = "助餐服务",
        serviceTime = 45,
        lastServiceTime = "2023-10-27 12:00"
    )
    val sampleOrderInfo = ServiceOrderInfoModel(
        startTime = "09:00",
        endTime = "12:00"
    )
    MaterialTheme {
        ServiceRecordItem(project = sampleProject, orderInfo = sampleOrderInfo)
    }
}
