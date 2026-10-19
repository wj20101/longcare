package com.ytone.longcare.common.utils

import android.content.Context
import android.os.Bundle
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.integration.txface.R
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.faceauth.FaceVerifyCallback
import com.ytone.longcare.common.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.FaceVerificationSession
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 腾讯人脸识别管理器
 *
 * 仅负责 SDK 生命周期与回调编排；参数组装与凭据拉取由 FaceVerificationParamAssembler 处理。
 */
@Singleton
class FaceVerificationManager @Inject constructor(
    private val tencentFaceRepository: TencentFaceRepository,
    private val runtimeConfigProvider: RuntimeConfigProvider,
    private val session: FaceVerificationSession,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : FaceVerifier {

    private val paramAssembler = FaceVerificationParamAssembler(tencentFaceRepository)
    private var generation = 0L
    private var preparationJob: Job? = null
    private var sessionWatchJob: Job? = null
    private var sdkActive = false

    override suspend fun startFaceVerification(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        callback: FaceVerifyCallback
    ) {
        release()
        val attempt = generation
        val expectedSession = config.sessionGeneration
        if (!session.isCurrent(expectedSession)) throw CancellationException("Face verification session changed")
        val guardedCallback = guardCallback(callback, attempt, expectedSession)
        sessionWatchJob = applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.sessionGeneration.first { it != expectedSession }
            withContext(Dispatchers.Main.immediate) {
                if (generation == attempt) {
                    release()
                    callback.onVerifyCancel()
                }
            }
        }
        try {
            val paramResult = coroutineScope {
                val preparation = coroutineContext.job
                preparationJob = preparation
                try {
                    paramAssembler.build(config, request)
                } finally {
                    if (preparationJob === preparation) preparationJob = null
                }
            }
            if (!isCurrent(attempt, expectedSession)) throw CancellationException("Face verification attempt ended")
            when (paramResult) {
                is FaceVerifyParamBuildResult.Success -> {
                    startSdkVerification(context, paramResult.params, guardedCallback, attempt, expectedSession)
                }
                FaceVerifyParamBuildResult.Failure -> {
                    guardedCallback.onInitFailed(
                        createError(context.getString(R.string.tencent_face_prepare_failed))
                    )
                }
            }
        } catch (e: CancellationException) {
            if (generation == attempt) release()
            throw e
        } catch (_: Exception) {
            guardedCallback.onInitFailed(
                createError(context.getString(R.string.tencent_face_prepare_failed))
            )
        }
    }

    private fun startSdkVerification(
        context: Context,
        params: FaceVerifyParams,
        callback: FaceVerifyCallback,
        attempt: Long,
        expectedSession: Long,
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

            if (!isCurrent(attempt, expectedSession)) return
            sdkActive = true
            WbCloudFaceVerifySdk.getInstance().initSdk(
                context,
                data,
                createSdkLoginListener(context, callback, attempt, expectedSession)
            )
        } catch (_: Exception) {
            callback.onVerifyFailed(
                createError(context.getString(R.string.tencent_face_start_failed))
            )
        }
    }

    private fun createSdkLoginListener(
        context: Context,
        callback: FaceVerifyCallback,
        attempt: Long,
        expectedSession: Long,
    ): WbCloudFaceVerifyLoginListener {
        return object : WbCloudFaceVerifyLoginListener {
            override fun onLoginSuccess() {
                if (!isCurrent(attempt, expectedSession)) return
                callback.onInitSuccess()
                // The UI may release the SDK from its initialization callback.
                if (!isCurrent(attempt, expectedSession)) return
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
        generation++
        preparationJob?.cancel()
        preparationJob = null
        sessionWatchJob?.cancel()
        sessionWatchJob = null
        if (!sdkActive) return
        sdkActive = false
        try {
            WbCloudFaceVerifySdk.getInstance().release()
        } catch (exception: Exception) {
            logE("释放腾讯人脸 SDK 失败", throwable = exception)
        }
    }

    private fun isCurrent(attempt: Long, expectedSession: Long): Boolean =
        generation == attempt && session.isCurrent(expectedSession)

    private fun guardCallback(
        callback: FaceVerifyCallback,
        attempt: Long,
        expectedSession: Long,
    ): FaceVerifyCallback = object : FaceVerifyCallback {
        private fun terminal(deliver: () -> Unit) {
            if (!isCurrent(attempt, expectedSession)) return
            release()
            deliver()
        }

        override fun onInitSuccess() {
            if (isCurrent(attempt, expectedSession)) callback.onInitSuccess()
        }
        override fun onInitFailed(error: FaceVerifyError?) = terminal { callback.onInitFailed(error) }
        override fun onVerifySuccess(result: FaceVerifyResult) = terminal { callback.onVerifySuccess(result) }
        override fun onVerifyFailed(error: FaceVerifyError?) = terminal { callback.onVerifyFailed(error) }
        override fun onVerifyCancel() = terminal { callback.onVerifyCancel() }
    }

    private companion object {
        const val SDK_CANCEL_DESCRIPTION_TOKEN = "取消"
    }
}
