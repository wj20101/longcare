package com.ytone.longcare.common.faceauth

import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult

/** Platform SDK callbacks translated into transportable application events. */
sealed interface FaceSdkEvent {
    data object InitSuccess : FaceSdkEvent

    data class InitFailed(val error: FaceVerifyError?) : FaceSdkEvent

    data class VerifySuccess(val result: FaceVerifyResult) : FaceSdkEvent

    data class VerifyFailed(val error: FaceVerifyError?) : FaceSdkEvent

    data object Cancelled : FaceSdkEvent
}
