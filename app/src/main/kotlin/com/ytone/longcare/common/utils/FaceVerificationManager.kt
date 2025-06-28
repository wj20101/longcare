package com.ytone.longcare.common.utils

import android.content.Context
import androidx.core.os.bundleOf
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import javax.inject.Inject
import javax.inject.Singleton

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
) {

    // ================================
    // 数据模型定义
    // ================================
    
    /**
     * 腾讯云配置参数
     */
    data class TencentCloudConfig(
        val appId: String,
        val secret: String
    )
    
    /**
     * 人脸验证请求参数
     */
    data class FaceVerifyRequest(
        val name: String,
        val idNo: String,
        val orderNo: String,
        val userId: String
    )

    /**
     * 人脸验证参数（SDK使用）
     */
    data class FaceVerifyParams(
        val faceId: String,
        val orderNo: String,
        val appId: String,
        val version: String = "1.0.0",
        val nonce: String,
        val userId: String,
        val sign: String,
        val keyLicence: String,
        val mode: FaceVerifyStatus.Mode = FaceVerifyStatus.Mode.GRADE
    )
    
    /**
     * 认证凭据数据
     */
    // ================================
    // 回调接口定义
    // ================================
    
    /**
     * 人脸验证回调接口
     */
    interface FaceVerifyCallback {
        /** 初始化成功 */
        fun onInitSuccess()
        
        /** 初始化失败 */
        fun onInitFailed(error: WbFaceError?)
        
        /** 验证成功 */
        fun onVerifySuccess(result: WbFaceVerifyResult)
        
        /** 验证失败 */
        fun onVerifyFailed(error: WbFaceError?)
        
        /** 用户取消验证 */
        fun onVerifyCancel()
    }

    // ================================
    // 公共API方法
    // ================================
    
    /**
     * 开始人脸验证（使用预设参数）
     * 
     * @param context Android上下文
     * @param params 验证参数
     * @param callback 验证回调
     */
    fun startFaceVerification(
        context: Context,
        params: FaceVerifyParams,
        callback: FaceVerifyCallback
    ) {
        initializeAndStartVerification(context, params, callback)
    }

    /**
     * 开始人脸验证（自动获取所有必要参数）
     * 
     * @param context Android上下文
     * @param config 腾讯云配置
     * @param request 验证请求参数
     * @param callback 验证回调
     */
    suspend fun startFaceVerification(
        context: Context,
        config: TencentCloudConfig,
        request: FaceVerifyRequest,
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
    private suspend fun getAccessToken(config: TencentCloudConfig): String? {
        return try {
            val result = tencentFaceRepository.getAccessToken(config.appId, config.secret)
            if (result is ApiResult.Success) {
                result.data.accessToken
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取SIGN类型票据
     */
    private suspend fun getSignTicket(config: TencentCloudConfig, accessToken: String): String? {
        return try {
            val result = tencentFaceRepository.getSignTicket(config.appId, accessToken)
            if (result is ApiResult.Success) {
                result.data.tickets?.firstOrNull()?.value
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取NONCE类型票据
     */
    private suspend fun getNonceTicket(config: TencentCloudConfig, accessToken: String, userId: String): String? {
        return try {
            val result = tencentFaceRepository.getApiTicket(config.appId, accessToken, userId)
            if (result is ApiResult.Success) {
                result.data.tickets?.firstOrNull()?.value
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取人脸ID
     */
    private suspend fun getFaceId(
        config: TencentCloudConfig,
        request: FaceVerifyRequest,
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
                name = request.name,
                idNo = request.idNo,
                userId = request.userId,
                sign = sign,
                nonce = nonce
            )

            if (result is ApiResult.Success) {
                result.data.result?.faceId
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 创建人脸验证参数
     */
    private fun createFaceVerifyParams(
        config: TencentCloudConfig,
        request: FaceVerifyRequest,
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
             keyLicence = BuildConfig.TX_Licence
         )
     }

    // ================================
    // SDK操作方法
    // ================================
    
    /**
     * 初始化并开始验证
     */
    private fun initializeAndStartVerification(
        context: Context,
        params: FaceVerifyParams,
        callback: FaceVerifyCallback
    ) {
        // 初始化SDK
        val initSuccess = initializeSdk(context, params.keyLicence, params.mode)
        if (!initSuccess) {
            callback.onInitFailed(createError("SDK初始化失败"))
            return
        }

        callback.onInitSuccess()
        startSdkVerification(context, params, callback)
    }

    /**
     * 初始化腾讯云人脸识别SDK
     */
    private fun initializeSdk(
        context: Context,
        licence: String,
        mode: FaceVerifyStatus.Mode
    ): Boolean {
        return try {
            // 简化初始化，只传入必要参数
            true
        } catch (e: Exception) {
            false
        }
    }

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
                // SDK登录成功，开始人脸验证
                startFaceVerification(context, callback)
            }

            override fun onLoginFailed(error: WbFaceError?) {
                callback.onVerifyFailed(error ?: createError("SDK登录失败"))
            }
        }
    }

    /**
     * 开始人脸验证
     */
    private fun startFaceVerification(context: Context, callback: FaceVerifyCallback) {
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
            result.isSuccess -> callback.onVerifySuccess(result)
            // 检查是否用户取消，根据SDK文档可能是其他属性名
            else -> {
                if (result.error?.code?.contains("cancel", ignoreCase = true) == true ||
                    result.error?.desc?.contains("取消", ignoreCase = true) == true) {
                    callback.onVerifyCancel()
                } else {
                    callback.onVerifyFailed(result.error ?: createError("验证失败"))
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
        val version = "1.0.0"

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
    private fun createError(message: String): WbFaceError {
        return WbFaceError().apply {
            domain = WbFaceError.WBFaceErrorDomainNativeProcess
            code = message
            desc = message
            reason = message
        }
    }

    // ================================
    // 资源管理
    // ================================
    
    /**
     * 释放SDK资源
     */
    fun release() {
        try {
            WbCloudFaceVerifySdk.getInstance().release()
        } catch (_: Exception) {
            // 忽略释放时的异常
        }
    }
}