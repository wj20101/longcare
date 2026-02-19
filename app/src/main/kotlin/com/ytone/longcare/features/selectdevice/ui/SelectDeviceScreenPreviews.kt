package com.ytone.longcare.features.selectdevice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun SelectDeviceScreenPreview() {
    // 预览不可用，因为需要OrderKey
}

@Preview
@Composable
fun DeviceGridPreview() {
    val devices = remember {
        List(6) { index -> Device(id = "id_$index", name = "设备名称") }
    }
    var selectedDeviceIndex by remember { mutableStateOf<Int?>(null) }
    DeviceGrid(
        devices = devices,
        selectedDeviceIndex = selectedDeviceIndex,
        onDeviceSelected = { index ->
            selectedDeviceIndex = if (selectedDeviceIndex == index) null else index
        }
    )
}

@Preview
@Composable
fun DeviceItemPreview() {
    val device = Device(id = "id_0", name = "设备名称")
    DeviceItem(device = device, isSelected = false, onClick = {})
}

@Preview
@Composable
fun NextStepButtonPreview() {
    NextStepButton(text = "Next Step", enabled = true, onClick = {})
}

@Preview
@Composable
fun SelectDeviceScreenWithNavControllerPreview() {
    // 预览不可用，因为需要OrderKey
}
