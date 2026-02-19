package com.ytone.longcare.features.nursingexecution.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.isExecutingState
import com.ytone.longcare.model.isPendingExecutionState
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NursingExecutionContent(
    actions: NursingExecutionActions,
    orderInfo: ServiceOrderInfoModel,
    orderKey: OrderKey,
    onNavigateToCountdown: suspend (List<ServiceProjectM>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.nursing_execution_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 90.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.nursing_execution_instruction),
                        fontSize = 14.sp,
                        color = Color.White,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Box {
                        ClientInfoCard(
                            modifier = Modifier.padding(top = 8.dp),
                            orderInfo = orderInfo
                        )

                        ServiceHoursTag(tagText = stringResource(R.string.nursing_execution_client_info_tag))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
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
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    ConfirmButton(
                        text = stringResource(R.string.nursing_execution_confirm_button),
                        onClick = singleClick {
                            when {
                                orderInfo.state.isExecutingState() -> {
                                    coroutineScope.launch {
                                        onNavigateToCountdown(orderInfo.projectList ?: emptyList())
                                    }
                                }
                                orderInfo.state.isPendingExecutionState() -> {
                                    actions.onNavigateToSelectDevice(orderKey)
                                }
                                else -> actions.onNavigateBack()
                            }
                        }
                    )
                }
            }
        }
    }
}
