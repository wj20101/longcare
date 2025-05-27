package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.nursing.viewmodel.NursingViewModel
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.CardWhite
import com.ytone.longcare.ui.DatePillSelectedBackground
import com.ytone.longcare.ui.DatePillSelectedText
import com.ytone.longcare.ui.DatePillUnselectedText
import com.ytone.longcare.ui.DividerColor
import com.ytone.longcare.ui.LightBlueBackground
import com.ytone.longcare.ui.LightGrayText
import com.ytone.longcare.ui.PrimaryBlue
import com.ytone.longcare.ui.ServiceItemHourColor
import com.ytone.longcare.ui.StatusGreen
import com.ytone.longcare.ui.StatusRed
import com.ytone.longcare.ui.TextPrimary
import com.ytone.longcare.ui.TextSecondary

enum class TaskStatus { PENDING, COMPLETED }
data class TaskInfo(
    val id: String,
    val clientName: String,
    val hours: String,
    val serviceType: String,
    val address: String,
    val status: TaskStatus
)

data class DateInfo(
    val dayLabel: String, // "昨天", "今天", "周二"
    val dateString: String, // "05/10"
    val fullDate: Long // Unique identifier for the date, e.g., epoch millis
)

@Composable
fun NursingScreen(
    navController: NavController, // Added NavController for navigation
    viewModel: NursingViewModel = hiltViewModel()
) {
    var selectedDateEpoch by remember { mutableStateOf(getDummyDates().find { it.dayLabel == "今天" }?.fullDate ?: 0L) }

    Scaffold(
        topBar = {
            NursingTopAppBar(
                title = "护理工作",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = LightBlueBackground // Main background below blue header
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
        ) {
            // Blue header section for Date Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue)
                    .padding(bottom = 16.dp) // Space before the task list card
            ) {
                DateSelector(
                    dates = getDummyDates(),
                    selectedDateEpoch = selectedDateEpoch,
                    onDateSelected = { dateInfo -> selectedDateEpoch = dateInfo.fullDate }
                )
            }

            // Task List Card - sits on LightBlueBackground
            TaskListSection(
                tasks = getDummyTasksForDate(selectedDateEpoch),
                modifier = Modifier
                    .padding(horizontal = 16.dp) // Side margins for the card
                    .fillMaxSize() // Takes remaining space
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingTopAppBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        actions = { Spacer(modifier = Modifier.width(48.dp)) }, // Balance nav icon for centering title
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun DateSelector(
    dates: List<DateInfo>,
    selectedDateEpoch: Long,
    onDateSelected: (DateInfo) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dates) { dateInfo ->
            DatePill(
                dateInfo = dateInfo,
                isSelected = dateInfo.fullDate == selectedDateEpoch,
                onClick = { onDateSelected(dateInfo) }
            )
        }
    }
}

@Composable
fun DatePill(dateInfo: DateInfo, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) DatePillSelectedBackground else Color.Transparent
    val dayLabelColor = if (isSelected) DatePillSelectedText else DatePillUnselectedText
    val dateStringColor = if (isSelected) DatePillSelectedText.copy(alpha = 0.7f) else DatePillUnselectedText.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp)) // Pill shape
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dateInfo.dayLabel,
            color = dayLabelColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = dateInfo.dateString,
            color = dateStringColor,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp
        )
    }
}

@Composable
fun TaskListSection(tasks: List<TaskInfo>, modifier: Modifier = Modifier) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("当前日期无护理任务", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)), // Top rounded corners
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), // Fill the card
        ) {
            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                TaskItem(taskInfo = task, onClick = { /* TODO: Handle task click */ })
                if (index < tasks.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = DividerColor
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItem(taskInfo: TaskInfo, onClick: () -> Unit) {
    val statusColor = if (taskInfo.status == TaskStatus.COMPLETED) StatusGreen else StatusRed
    val statusText = if (taskInfo.status == TaskStatus.COMPLETED) "已完成" else "未完成"

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        val (detailsColumn, statusTextRef, arrowIconRef) = createRefs()
        val endGuidelineForArrow = createGuidelineFromEnd(24.dp)
        val endGuidelineForStatus = createGuidelineFromEnd(32.dp) // Status is to the left of arrow

        // Details Column (Name, Hours, Service, Address)
        Column(
            modifier = Modifier.constrainAs(detailsColumn) {
                start.linkTo(parent.start)
                end.linkTo(statusTextRef.start, margin = 8.dp, goneMargin = 70.dp) // Ensure space or constraint if status text is short
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
            }
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = taskInfo.clientName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "工时: ${taskInfo.hours}",
                    style = MaterialTheme.typography.labelSmall.copy(color = ServiceItemHourColor, fontSize = 13.sp),
                    modifier = Modifier.padding(bottom = 2.dp) // Align baseline
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = taskInfo.serviceType,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "地址: ${taskInfo.address}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextSecondary,
                maxLines = 1
            )
        }

        // Status Text
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.constrainAs(statusTextRef) {
                end.linkTo(arrowIconRef.start, margin = 4.dp)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                centerVerticallyTo(parent)
            }
        )

        // Arrow Icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "详情",
            tint = LightGrayText,
            modifier = Modifier.constrainAs(arrowIconRef) {
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                centerVerticallyTo(parent)
            }
        )
    }
}

// --- Dummy Data for Preview ---
fun getDummyDates(): List<DateInfo> {
    val today = System.currentTimeMillis()
    val dayMillis = 24 * 60 * 60 * 1000
    return listOf(
        DateInfo("昨天", "05/10", today - dayMillis),
        DateInfo("今天", "05/11", today),
        DateInfo("明天", "05/12", today + dayMillis),
        DateInfo("周二", "05/13", today + 2 * dayMillis),
        DateInfo("周三", "05/14", today + 3 * dayMillis),
        DateInfo("周四", "05/15", today + 4 * dayMillis),
        DateInfo("周五", "05/16", today + 5 * dayMillis)
    )
}

fun getDummyTasksForDate(dateEpoch: Long): List<TaskInfo> {
    // Simple logic: return different task lists based on date, or same for preview
    val todayEpoch = getDummyDates().find { it.dayLabel == "今天" }?.fullDate
    if (dateEpoch == todayEpoch) {
        return listOf(
            TaskInfo("task1", "孙天成", "8", "助浴服务", "杭州市西湖区328弄24号", TaskStatus.PENDING),
            TaskInfo("task2", "王东明", "8", "清洁服务", "杭州市西湖区328弄24号", TaskStatus.COMPLETED),
            TaskInfo("task3", "胡来德", "8", "维修服务", "杭州市滨江区XX路10号", TaskStatus.COMPLETED),
            TaskInfo("task4", "丛敏丽", "8", "理发服务", "杭州市上城区YY街22号", TaskStatus.COMPLETED),
            TaskInfo("task5", "爱德福", "8", "推拿服务", "杭州市西湖区328弄24号", TaskStatus.COMPLETED),
            TaskInfo("task6", "丁成立", "8", "清洁服务", "杭州市滨江区XX路10号", TaskStatus.COMPLETED),
            TaskInfo("task7", "张爱国", "8", "清洁服务", "杭州市上城区YY街22号", TaskStatus.COMPLETED),
            TaskInfo("task8", "王阳明", "8", "清洁服务", "杭州市西湖区328弄24号", TaskStatus.COMPLETED),
            TaskInfo("task9", "陈福记", "8", "送餐服务", "杭州市滨江区XX路10号", TaskStatus.PENDING)
        )
    }
    if (dateEpoch == getDummyDates().find { it.dayLabel == "昨天" }?.fullDate) {
        return listOf(
            TaskInfo("task10", "李晓红", "4", "购物代办", "杭州市拱墅区AA路1号", TaskStatus.COMPLETED),
            TaskInfo("task11", "赵铁柱", "6", "健康监测", "杭州市余杭区BB路2号", TaskStatus.COMPLETED)
        )
    }
    return emptyList() // Default to empty for other dates
}


@Preview(showBackground = true)
@Composable
fun NursingScreenPreview() {
    LongCareTheme {
        NursingScreen(navController = rememberNavController())
    }
}