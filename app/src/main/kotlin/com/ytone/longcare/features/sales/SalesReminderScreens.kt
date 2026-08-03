package com.ytone.longcare.features.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.model.ToDoResultModel

@Composable
internal fun SalesReminderListScreen(
    reminders: List<ToDoResultModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReminderClick: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "待办提醒",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (isLoading && reminders.isNotEmpty()) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = SalesBlue,
                    )
                }
            }
            when {
                isLoading && reminders.isEmpty() -> {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp)
                                    .salesWhiteCard(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = SalesBlue)
                        }
                    }
                }

                errorMessage != null && reminders.isEmpty() -> {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp)
                                    .salesWhiteCard()
                                    .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = errorMessage,
                                color = SalesTextPrimary,
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = onRetry) {
                                Text(text = "重新加载", color = SalesBlue)
                            }
                        }
                    }
                }

                reminders.isEmpty() -> {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp)
                                    .salesWhiteCard()
                                    .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "暂无待办提醒",
                                color = SalesTextPrimary,
                                fontSize = 17.sp,
                            )
                            Text(
                                text = "新的待办事项会显示在这里",
                                color = SalesTextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(
                        items = reminders,
                    ) { index, reminder ->
                        SalesReminderCard(
                            reminder = reminder,
                            onClick = { onReminderClick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesReminderCard(
    reminder: ToDoResultModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title.orEmpty().ifBlank { "待办事项" },
                color = SalesTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    reminder.createTime.orEmpty().ifBlank {
                        "提醒时间待安排"
                    },
                color = SalesTextSecondary,
                fontSize = 14.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看待办详情",
            tint = Color(0xFFB7C8DC),
        )
    }
}

@Composable
internal fun SalesReminderDetailScreen(
    reminder: ToDoResultModel?,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "待办提醒详情",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .salesWhiteCard()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Text(
                        text =
                            reminder?.title.orEmpty().ifBlank {
                                "待办事项"
                            },
                        modifier = Modifier.fillMaxWidth(),
                        color = SalesTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "事项详情：",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            reminder?.content.orEmpty().ifBlank {
                                "暂无详细说明"
                            },
                        color = SalesTextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = "提醒时间：",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            reminder?.createTime.orEmpty().ifBlank {
                                "待安排"
                            },
                        color = SalesTextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }
            item {
                SalesOutlinedActionButton(
                    text = "返回",
                    onClick = onBack,
                )
            }
        }
    }
}
