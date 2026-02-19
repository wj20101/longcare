package com.ytone.longcare.features.endservice.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.endservice.api.EndServiceSelectionActions
import com.ytone.longcare.features.endservice.vm.EndServiceSelectionViewModel
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.navigation.EndOderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

@Composable
internal fun BoxScope.EndServiceSelectionSuccessContent(
    actions: EndServiceSelectionActions,
    viewModel: EndServiceSelectionViewModel,
    countdownViewModel: ServiceCountdownViewModel,
    orderKey: OrderKey,
    endType: Int,
    projectList: List<ServiceProjectM>,
    selectedProjectIds: Set<Int>,
    context: Context,
    scope: CoroutineScope
) {
    val totalDuration = projectList
        .filter { selectedProjectIds.contains(it.projectId) }
        .sumOf { it.serviceTime }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        TotalDurationDisplay(totalDuration = totalDuration)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "请确认本次实际完成的服务项目",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(projectList) { project ->
                ServiceSelectionItem(
                    name = project.projectName,
                    duration = project.serviceTime,
                    isSelected = selectedProjectIds.contains(project.projectId),
                    onClick = { viewModel.toggleSelection(project.projectId) }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
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
            val isAllSelected = projectList.isNotEmpty() && selectedProjectIds.size == projectList.size
            SelectAllButton(
                isAllSelected = isAllSelected,
                enabled = true,
                onClick = {
                    if (isAllSelected) viewModel.deselectAll() else viewModel.selectAll()
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            NextStepButton(
                text = "确认结束服务",
                enabled = selectedProjectIds.isNotEmpty(),
                onClick = singleClick {
                    if (selectedProjectIds.isEmpty()) {
                        Toast.makeText(context, "请至少选择一个服务项目", Toast.LENGTH_SHORT).show()
                        return@singleClick
                    }

                    scope.launch {
                        val uploadedImages = countdownViewModel.getUploadedImagesSuspend(orderKey)
                        val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE]?.mapNotNull { it.key } ?: emptyList()
                        val centerImgList = uploadedImages[ImageTaskType.CENTER_CARE]?.mapNotNull { it.key } ?: emptyList()
                        val endImgList = uploadedImages[ImageTaskType.AFTER_CARE]?.mapNotNull { it.key } ?: emptyList()

                        KLogger.i(
                            "EndServiceSelection",
                            "Navigating to NFC. Images - Begin: ${beginImgList.size}, Center: ${centerImgList.size}, End: ${endImgList.size}"
                        )

                        actions.onNavigateToNfcSignInForEndOrder(
                            orderKey,
                            EndOderInfo(
                                projectIdList = viewModel.getConfirmedProjectIds(),
                                beginImgList = beginImgList,
                                centerImgList = centerImgList,
                                endImgList = endImgList,
                                endType = endType
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
