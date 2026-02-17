package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory

@Composable
fun SelectedServicesCard(
    orderKey: OrderKey,
    projectIdList: List<Int>,
    sharedViewModel: SharedOrderDetailViewModel
) {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    val orderInfo = sharedViewModel.getCachedOrderInfo(orderKey)
    val allProjects = orderInfo?.projectList ?: emptyList()
    val isAllSelected =
        projectIdList.isEmpty() || (allProjects.isNotEmpty() && projectIdList.containsAll(
            allProjects.map { it.projectId }
        ))
    val selectedProjects =
        if (isAllSelected) allProjects else allProjects.filter { it.projectId in projectIdList }

    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tagHeightEstimate - tagOverlap),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp
                )
            ) {
                if (selectedProjects.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedProjects.forEachIndexed { index, project ->
                            Text("${index + 1}: ${project.projectName} (${project.serviceTime}分钟)")
                        }
                    }
                } else {
                    Text(
                        text = "暂无选中的服务项目",
                        color = Color.Gray
                    )
                }
            }
        }
        ServiceHoursTag(
            modifier = Modifier.align(Alignment.TopStart),
            tagText = "所选服务",
            tagCategory = TagCategory.DEFAULT
        )
    }
}

@Preview
@Composable
fun SelectedServicesCardPreview() {
    SelectedServicesCard(
        orderKey = OrderKey(orderId = 12345L, planId = 0),
        projectIdList = listOf(1, 2),
        sharedViewModel = hiltViewModel()
    )
}

