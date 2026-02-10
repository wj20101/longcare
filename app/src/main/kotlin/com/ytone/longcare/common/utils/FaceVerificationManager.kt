package com.ytone.longcare.common.utils

import android.content.Context
import com.ytone.longcare.BuildConfig
import androidx.core.os.bundleOf
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.ytone.longcare.domain.faceauth.model.FACE_AUTH_API_VERSION
import com.ytone.longcare.domain.faceauth.model.FACE_AUTH_SOURCE_PHOTO_TYPE_HD
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
 * 职责：
 * 1. 管理腾讯云人脸识别的完整流程
 * 2. 处理认证凭据的获取和管理
 * 3. 协调SDK的初始化和验证过程
 * 4. 提供统一的错误处理和回调机制
 */
@Singleton
class FaceVerificationManager @Inject constructor(
    private val tencentFaceRepository: TencentFaceRepository
) : FaceVerifier {

    // ================================
    // 数据模型定义
    // ================================

    /**
     * 人脸验证参数（SDK使用）
     */
    data class FaceVerifyParams(
        val faceId: String,
        val orderNo: String,
        val appId: String,
        val version: String = FACE_AUTH_API_VERSION,
        val nonce: String,
        val userId: String,
        val sign: String,
        val keyLicence: String,
        val mode: FaceVerifyStatus.Mode = FaceVerifyStatus.Mode.GRADE
    )

    // ================================
    // 公共API方法
    // ================================

    /**
     * 开始人脸验证（自动获取所有必要参数）
     *
     * @param context Android上下文
     * @param config 腾讯云配置
     * @param request 验证请求参数
     * @param callback 验证回调
     */
    override suspend fun startFaceVerification(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        callback: FaceVerifyCallback
    ) {
        try {
            // 统一生成nonce，避免在不同地方创建不同的nonce
            val nonce = generateNonce()

            // 按需获取访问令牌
            val accessToken = getAccessToken(config)
            if (accessToken == null) {
                callback.onInitFailed(createError("获取访问令牌失败"))
                return
            }

            // 按需获取SIGN类型票据
            val signTicket = getSignTicket(config, accessToken)
            if (signTicket == null) {
                callback.onInitFailed(createError("获取SIGN票据失败"))
                return
            }

            // 获取faceId
            val faceId = getFaceId(config, request, signTicket, nonce)
            if (faceId == null) {
                callback.onInitFailed(createError("获取faceId失败"))
                return
            }

            // 按需获取NONCE类型票据
            val nonceTicket = getNonceTicket(config, accessToken, request.userId)
            if (nonceTicket == null) {
                callback.onInitFailed(createError("获取NONCE票据失败"))
                return
            }

            // 生成验证参数并开始验证
            val params = createFaceVerifyParams(config, request, faceId, nonceTicket, nonce)
            startSdkVerification(context, params, callback)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.onInitFailed(createError("人脸验证初始化失败: ${e.message}"))
        }
    }

    // ================================
    // 核心流程方法
    // ================================

    /**
     * 获取访问令牌
     */
    private suspend fun getAccessToken(config: FaceVerificationConfig): String? {
        return try {
            val result = tencentFaceRepository.getAccessToken(config.appId, config.secret)
            if (result is ApiResult.Success) {
                result.data.accessToken
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取SIGN类型票据
     */
    private suspend fun getSignTicket(config: FaceVerificationConfig, accessToken: String): String? {
        return try {
            val result = tencentFaceRepository.getSignTicket(config.appId, accessToken)
            if (result is ApiResult.Success) {
                result.data.tickets?.firstOrNull()?.value
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取NONCE类型票据
     */
    private suspend fun getNonceTicket(config: FaceVerificationConfig, accessToken: String, userId: String): String? {
        return try {
            val result = tencentFaceRepository.getApiTicket(config.appId, accessToken, userId)
            if (result is ApiResult.Success) {
                result.data.tickets?.firstOrNull()?.value
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取人脸ID
     */
    private suspend fun getFaceId(
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        signTicket: String,
        nonce: String
    ): String? {
        return try {
            val sign = generateSign(
                appId = config.appId,
                nonce = nonce,
                apiTicket = signTicket,
                userId = request.userId
            )

            val result = tencentFaceRepository.getFaceId(
                appId = config.appId,
                orderNo = request.orderNo,
                name = if (request.sourcePhotoStr != null) null else request.name,
                idNo = if (request.sourcePhotoStr != null) null else request.idNo,
                userId = request.userId,
                sign = sign,
                nonce = nonce,
                sourcePhotoStr = request.sourcePhotoStr,
                sourcePhotoType = if (request.sourcePhotoStr != null) FACE_AUTH_SOURCE_PHOTO_TYPE_HD else null
            )

            if (result is ApiResult.Success) {
                result.data.result?.faceId
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 创建人脸验证参数
     */
    private fun createFaceVerifyParams(
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        faceId: String,
        nonceTicket: String,
        nonce: String
    ): FaceVerifyParams {
        val sign = generateSign(
            appId = config.appId,
            nonce = nonce,
            apiTicket = nonceTicket,
            userId = request.userId,
        )

        return FaceVerifyParams(
            faceId = faceId,
            orderNo = request.orderNo,
            appId = config.appId,
            nonce = nonce,
            userId = request.userId,
            sign = sign,
            keyLicence = config.licence
        )
    }

    // ================================
    // SDK操作方法
    // ================================

    /**
     * 启动SDK验证流程
     */
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
                params.mode,
                params.keyLicence
            )

            // 设置SDK配置
            val data = bundleOf(
                WbCloudFaceContant.INPUT_DATA to inputData,
                WbCloudFaceContant.LANGUAGE to WbCloudFaceContant.LANGUAGE_ZH_CN,
                WbCloudFaceContant.COLOR_MODE to WbCloudFaceContant.WHITE,
                WbCloudFaceContant.VIDEO_UPLOAD to false,
                WbCloudFaceContant.PLAY_VOICE to false,
                WbCloudFaceContant.IS_LANDSCAPE to false,
                WbCloudFaceContant.COMPARE_TYPE to WbCloudFaceContant.ID_CARD,
                WbCloudFaceContant.IS_ENABLE_LOG to BuildConfig.DEBUG
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

    /**
     * 创建SDK登录监听器
     */
    private fun createSdkLoginListener(context: Context, callback: FaceVerifyCallback): WbCloudFaceVerifyLoginListener {
        return object : WbCloudFaceVerifyLoginListener {
            override fun onLoginSuccess() {
                callback.onInitSuccess()
                // SDK登录成功，开始人脸验证
                startSdkFaceVerification(context, callback)
            }

            override fun onLoginFailed(error: WbFaceError?) {
                callback.onVerifyFailed(error?.toDomainError() ?: createError("SDK登录失败"))
            }
        }
    }

    /**
     * 开始人脸验证
     */
    private fun startSdkFaceVerification(context: Context, callback: FaceVerifyCallback) {
        try {
            WbCloudFaceVerifySdk.getInstance().startWbFaceVerifySdk(context) { result ->
                handleVerificationResult(result, callback)
            }
        } catch (e: Exception) {
            callback.onVerifyFailed(createError("人脸验证失败: ${e.message}"))
        }
    }

    /**
     * 处理验证结果
     */
    private fun handleVerificationResult(
        result: WbFaceVerifyResult,
        callback: FaceVerifyCallback
    ) {
        when {
            result.isSuccess -> callback.onVerifySuccess(result.toDomainResult())
            // 检查是否用户取消，根据SDK文档可能是其他属性名
            else -> {
                if (result.error?.code?.contains("cancel", ignoreCase = true) == true ||
                    result.error?.desc?.contains("取消", ignoreCase = true) == true) {
                    callback.onVerifyCancel()
                } else {
                    callback.onVerifyFailed(result.error?.toDomainError() ?: createError("验证失败"))
                }
            }
        }
    }

    // ================================
    // 工具方法
    // ================================

    /**
     * 生成随机字符串（nonce）
     */
    private fun generateNonce(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }


    /**
     * 生成签名
     * 根据腾讯云官方文档实现签名算法：
     * 1. 将 appId、userId、version、ticket、nonce 五个参数按字典序排序
     * 2. 拼接成字符串
     * 3. 进行 SHA1 编码
     */
    private fun generateSign(
        appId: String, nonce: String, userId: String, apiTicket: String
    ): String {
        // 固定版本号
        val version = FACE_AUTH_API_VERSION

        // 将五个参数按字典序排序
        val params = listOf(version, appId, apiTicket, nonce, userId).sorted()

        // 拼接成字符串
        val signString = params.joinToString("")

        // 进行 SHA1 编码
        return sha1(signString).uppercase()
    }

    /**
     * SHA1编码
     */
    private fun sha1(input: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            val result = digest.digest(input.toByteArray())
            result.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 创建错误对象
     */
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

    // ================================
    // 资源管理
    // ================================

    /**
     * 释放SDK资源
     */
    override fun release() {
        try {
            WbCloudFaceVerifySdk.getInstance().release()
        } catch (_: Exception) {
            // 忽略释放时的异常
        }
    }

}
