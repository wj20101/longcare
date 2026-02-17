package com.ytone.longcare.features.selectservice.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun TotalDurationDisplayPreview() {
    TotalDurationDisplay(totalDuration = 120)
}

@Preview
@Composable
fun ServiceSelectionListPreview() {
    val serviceItems = listOf(
        ServiceItem(id = 1, name = "基础护理", duration = 60, isSelected = true),
        ServiceItem(id = 2, name = "康复训练", duration = 45),
        ServiceItem(id = 3, name = "心理疏导", duration = 30, isSelected = false)
    )
    ServiceSelectionList(serviceItems = serviceItems, onItemClick = {})
}

@Preview
@Composable
fun ServiceSelectionItemSelectedPreview() {
    ServiceSelectionItem(
        item = ServiceItem(
            id = 1,
            name = "基础护理",
            duration = 60,
            isSelected = true
        ),
        onClick = {}
    )
}

@Preview
@Composable
fun ServiceSelectionItemUnselectedPreview() {
    ServiceSelectionItem(
        item = ServiceItem(
            id = 2,
            name = "康复训练",
            duration = 45,
            isSelected = false
        ),
        onClick = {}
    )
}

@Preview
@Composable
fun NextStepButtonEnabledPreview() {
    NextStepButton(text = "开始服务", enabled = true, onClick = {})
}

@Preview
@Composable
fun NextStepButtonDisabledPreview() {
    NextStepButton(text = "开始服务", enabled = false, onClick = {})
}

@Preview
@Composable
fun SelectAllButtonPreview() {
    SelectAllButton(isAllSelected = true, enabled = true, onClick = {})
}
