package com.ytone.longcare.features.identification.vm

sealed interface IdentificationEvent {
    data object NavigateToFaceCapture : IdentificationEvent

    data class ShowToast(val message: String) : IdentificationEvent
}
