package com.ytone.longcare.features.identification.vm

internal suspend fun emitFaceCaptureRequiredEvents(
    emitEvent: suspend (IdentificationEvent) -> Unit,
) {
    emitEvent(IdentificationEvent.ShowToast("请先设置人脸信息"))
    emitEvent(IdentificationEvent.NavigateToFaceCapture)
}
