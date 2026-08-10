package com.ytone.longcare.platform.face

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.common.faceauth.FaceVerifyCallback
import com.ytone.longcare.common.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** UI-owned boundary for the Tencent face SDK, which requires the current UI Context. */
class FaceSdkUiController internal constructor(
    private val faceVerifier: FaceVerifier,
) {
    suspend fun start(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        onEvent: (FaceSdkEvent) -> Unit,
    ) {
        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = object : FaceVerifyCallback {
                override fun onInitSuccess() = onEvent(FaceSdkEvent.InitSuccess)

                override fun onInitFailed(error: FaceVerifyError?) {
                    onEvent(FaceSdkEvent.InitFailed(error))
                    faceVerifier.release()
                }

                override fun onVerifySuccess(result: FaceVerifyResult) {
                    onEvent(FaceSdkEvent.VerifySuccess(result))
                    faceVerifier.release()
                }

                override fun onVerifyFailed(error: FaceVerifyError?) {
                    onEvent(FaceSdkEvent.VerifyFailed(error))
                    faceVerifier.release()
                }

                override fun onVerifyCancel() {
                    onEvent(FaceSdkEvent.Cancelled)
                    faceVerifier.release()
                }
            },
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface FaceSdkUiEntryPoint {
    fun faceVerifier(): FaceVerifier
}

@Composable
fun rememberFaceSdkUiController(): FaceSdkUiController {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        FaceSdkUiController(
            EntryPointAccessors.fromApplication(
                applicationContext,
                FaceSdkUiEntryPoint::class.java,
            ).faceVerifier(),
        )
    }
}
