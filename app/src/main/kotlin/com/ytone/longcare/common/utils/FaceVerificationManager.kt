package com.ytone.longcare.common.utils

import android.content.Context
import android.os.Bundle
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.R
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.faceauth.FaceVerifyCallback
import com.ytone.longcare.common.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * 腾讯人脸识别管理器
 *
 * 仅负责 SDK 生命周期与回调编排；参数组装与凭据拉取由 FaceVerificationParamAssembler 处理。
 */
@Singleton
class FaceVerificationManager @Inject constructor(
    private val tencentFaceRepository: TencentFaceRepository,
    private val runtimeConfigProvider: RuntimeConfigProvider
) : FaceVerifier {

    private val paramAssembler = FaceVerificationParamAssembler(tencentFaceRepository)

    override suspend fun startFaceVerification(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        callback: FaceVerifyCallback
    ) {
        try {
            when (val paramResult = paramAssembler.build(config, request)) {
                is FaceVerifyParamBuildResult.Success -> {
                    startSdkVerification(context, paramResult.params, callback)
                }
                FaceVerifyParamBuildResult.Failure -> {
                    callback.onInitFailed(
                        createError(context.getString(R.string.tencent_face_prepare_failed))
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            callback.onInitFailed(
                createError(context.getString(R.string.tencent_face_prepare_failed))
            )
        }
    }

    private fun startSdkVerification(
        context: Context,
        params: FaceVerifyParams,
        callback: FaceVerifyCallback
    ) {
        try {
            val inputData = WbCloudFaceVerifySdk.InputData(
                params.faceId,
                params.orderNo,
                params.appId,
                params.version,
                params.nonce,
                params.userId,
                params.sign,
                FaceVerifyStatus.Mode.GRADE,
                params.keyLicence
            )

            val data = Bundle().apply {
                putSerializable(WbCloudFaceContant.INPUT_DATA, inputData)
                putString(WbCloudFaceContant.LANGUAGE, WbCloudFaceContant.LANGUAGE_ZH_CN)
                putString(WbCloudFaceContant.COLOR_MODE, WbCloudFaceContant.WHITE)
                putBoolean(WbCloudFaceContant.VIDEO_UPLOAD, false)
                putBoolean(WbCloudFaceContant.PLAY_VOICE, false)
                putBoolean(WbCloudFaceContant.IS_LANDSCAPE, false)
                putString(WbCloudFaceContant.COMPARE_TYPE, WbCloudFaceContant.ID_CARD)
                putBoolean(WbCloudFaceContant.IS_ENABLE_LOG, runtimeConfigProvider.isDebug)
            }

            WbCloudFaceVerifySdk.getInstance().initSdk(
                context,
                data,
                createSdkLoginListener(context, callback)
            )
        } catch (_: Exception) {
            callback.onVerifyFailed(
                createError(context.getString(R.string.tencent_face_start_failed))
            )
        }
    }

    private fun createSdkLoginListener(
        context: Context,
        callback: FaceVerifyCallback
    ): WbCloudFaceVerifyLoginListener {
        return object : WbCloudFaceVerifyLoginListener {
            override fun onLoginSuccess() {
                callback.onInitSuccess()
                startSdkFaceVerification(context, callback)
            }

            override fun onLoginFailed(error: WbFaceError?) {
                callback.onVerifyFailed(
                    error?.toDomainError()
                        ?: createError(context.getString(R.string.tencent_face_start_failed))
                )
            }
        }
    }

    private fun startSdkFaceVerification(context: Context, callback: FaceVerifyCallback) {
        try {
            WbCloudFaceVerifySdk.getInstance().startWbFaceVerifySdk(context) { result ->
                handleVerificationResult(context, result, callback)
            }
        } catch (_: Exception) {
            callback.onVerifyFailed(
                createError(context.getString(R.string.tencent_face_unavailable))
            )
        }
    }

    private fun handleVerificationResult(
        context: Context,
        result: WbFaceVerifyResult,
        callback: FaceVerifyCallback
    ) {
        when {
            result.isSuccess -> callback.onVerifySuccess(result.toDomainResult())
            else -> {
                if (result.error?.code?.contains("cancel", ignoreCase = true) == true ||
                    result.error?.desc?.contains(SDK_CANCEL_DESCRIPTION_TOKEN, ignoreCase = true) == true
                ) {
                    callback.onVerifyCancel()
                } else {
                    callback.onVerifyFailed(
                        result.error?.toDomainError()
                            ?: createError(
                                context.getString(R.string.tencent_face_verification_failed),
                            )
                    )
                }
            }
        }
    }

    private fun createError(message: String): FaceVerifyError {
        return FaceVerifyError(
            domain = WbFaceError.WBFaceErrorDomainNativeProcess,
            code = message,
            description = message,
            reason = message
        )
    }

    private fun WbFaceError.toDomainError(): FaceVerifyError {
        return FaceVerifyError(
            domain = domain,
            code = code,
            description = desc,
            reason = reason
        )
    }

    private fun WbFaceVerifyResult.toDomainResult(): FaceVerifyResult {
        return FaceVerifyResult(
            isSuccess = isSuccess,
            error = error?.toDomainError()
        )
    }

    override fun release() {
        try {
            WbCloudFaceVerifySdk.getInstance().release()
        } catch (exception: Exception) {
            logE("释放腾讯人脸 SDK 失败", throwable = exception)
        }
    }

    private companion object {
        const val SDK_CANCEL_DESCRIPTION_TOKEN = "取消"
    }
}
