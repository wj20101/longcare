package com.ytone.longcare.platform.face

import android.content.Context
import androidx.compose.runtime.DisposableEffect
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
    private var generation = 0L
    private var active = false

    fun release() {
        generation++
        if (active) {
            active = false
            faceVerifier.release()
        }
    }

    suspend fun start(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        onEvent: (FaceSdkEvent) -> Unit,
    ) {
        release()
        active = true
        val token = ++generation
        fun deliver(event: FaceSdkEvent, terminal: Boolean = true) {
            if (!active || token != generation) return
            if (terminal) release()
            onEvent(event)
        }
        try {
        faceVerifier.startFaceVerification(
            context = context,
            config = config,
            request = request,
            callback = object : FaceVerifyCallback {
                override fun onInitSuccess() = deliver(FaceSdkEvent.InitSuccess, terminal = false)

                override fun onInitFailed(error: FaceVerifyError?) {
                    deliver(FaceSdkEvent.InitFailed(error))
                }

                override fun onVerifySuccess(result: FaceVerifyResult) {
                    deliver(FaceSdkEvent.VerifySuccess(result))
                }

                override fun onVerifyFailed(error: FaceVerifyError?) {
                    deliver(FaceSdkEvent.VerifyFailed(error))
                }

                override fun onVerifyCancel() {
                    deliver(FaceSdkEvent.Cancelled)
                }
            },
        )
        } catch (error: Throwable) {
            if (token == generation) release()
            throw error
        }
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
    val controller = remember(applicationContext) {
        FaceSdkUiController(
            EntryPointAccessors.fromApplication(
                applicationContext,
                FaceSdkUiEntryPoint::class.java,
            ).faceVerifier(),
        )
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}
