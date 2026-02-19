package com.ytone.longcare.common.utils

import android.content.Context
import androidx.core.os.bundleOf
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
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
                is FaceVerifyParamBuildResult.Failure -> {
                    callback.onInitFailed(createError(paramResult.message))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.onInitFailed(createError("人脸验证初始化失败: ${e.message}"))
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

            val data = bundleOf(
                WbCloudFaceContant.INPUT_DATA to inputData,
                WbCloudFaceContant.LANGUAGE to WbCloudFaceContant.LANGUAGE_ZH_CN,
                WbCloudFaceContant.COLOR_MODE to WbCloudFaceContant.WHITE,
                WbCloudFaceContant.VIDEO_UPLOAD to false,
                WbCloudFaceContant.PLAY_VOICE to false,
                WbCloudFaceContant.IS_LANDSCAPE to false,
                WbCloudFaceContant.COMPARE_TYPE to WbCloudFaceContant.ID_CARD,
                WbCloudFaceContant.IS_ENABLE_LOG to runtimeConfigProvider.isDebug
            )

            WbCloudFaceVerifySdk.getInstance().initSdk(
                context,
                data,
                createSdkLoginListener(context, callback)
            )
        } catch (e: Exception) {
            callback.onVerifyFailed(createError("启动验证失败: ${e.message}"))
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
                callback.onVerifyFailed(error?.toDomainError() ?: createError("SDK登录失败"))
            }
        }
    }

    private fun startSdkFaceVerification(context: Context, callback: FaceVerifyCallback) {
        try {
            WbCloudFaceVerifySdk.getInstance().startWbFaceVerifySdk(context) { result ->
                handleVerificationResult(result, callback)
            }
        } catch (e: Exception) {
            callback.onVerifyFailed(createError("人脸验证失败: ${e.message}"))
        }
    }

    private fun handleVerificationResult(
        result: WbFaceVerifyResult,
        callback: FaceVerifyCallback
    ) {
        when {
            result.isSuccess -> callback.onVerifySuccess(result.toDomainResult())
            else -> {
                if (result.error?.code?.contains("cancel", ignoreCase = true) == true ||
                    result.error?.desc?.contains("取消", ignoreCase = true) == true
                ) {
                    callback.onVerifyCancel()
                } else {
                    callback.onVerifyFailed(result.error?.toDomainError() ?: createError("验证失败"))
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
        } catch (_: Exception) {
            // ignore
        }
    }
}
