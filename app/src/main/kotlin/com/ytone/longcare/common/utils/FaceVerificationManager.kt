package com.ytone.longcare.common.utils

import android.content.Context
import androidx.core.os.bundleOf
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.faceauth.TencentFaceRepository
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceContant
import com.tencent.cloud.huiyansdkface.facelight.api.WbCloudFaceVerifySdk
import com.tencent.cloud.huiyansdkface.facelight.api.listeners.WbCloudFaceVerifyLoginListener
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceError
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.tencent.cloud.huiyansdkface.facelight.process.FaceVerifyStatus
import com.ytone.longcare.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton


/**
 * 腾讯人脸识别管理器
 * 负责处理腾讯云人脸识别SDK的初始化和验证流程
 */
@Singleton
class FaceVerificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tencentFaceRepository: TencentFaceRepository
) {

    /**
     * 人脸验证参数
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
     * 腾讯云配置参数
     */
    data class TencentCloudConfig(
        val appId: String, val secret: String
    )

    /**
     * 人脸验证回调接口
     */
    interface FaceVerifyCallback {
        /**
         * 初始化成功
         */
        fun onInitSuccess()

        /**
         * 初始化失败
         */
        fun onInitFailed(error: WbFaceError?)

        /**
         * 验证成功
         */
        fun onVerifySuccess(result: WbFaceVerifyResult)

        /**
         * 验证失败
         */
        fun onVerifyFailed(error: WbFaceError?)

        /**
         * 用户取消验证
         */
        fun onVerifyCancel()
    }

    /**
     * 开始人脸验证（使用预设参数）
     * @param params 验证参数
     * @param callback 验证回调
     */
    fun startFaceVerification(
        params: FaceVerifyParams, callback: FaceVerifyCallback
    ) {
        startVerificationInternal(params, callback)
    }

    /**
     * 开始人脸验证（自动获取签名参数）
     * @param config 腾讯云配置
     * @param faceId 人脸ID
     * @param orderNo 订单号
     * @param userId 用户ID
     * @param callback 验证回调
     */
    suspend fun startFaceVerificationWithAutoSign(
        config: TencentCloudConfig,
        faceId: String,
        orderNo: String,
        userId: String,
        callback: FaceVerifyCallback
    ) {
        try {
            // 1. 获取access_token
            val accessTokenResult = tencentFaceRepository.getAccessToken(
                appId = config.appId, secret = config.secret
            )

            when (accessTokenResult) {
                is ApiResult.Success -> {
                    val accessToken = accessTokenResult.data.accessToken
                    if (accessToken.isNullOrEmpty()) {
                        callback.onInitFailed(null)
                        return
                    }

                    // 2. 获取api_ticket
                    val apiTicketResult = tencentFaceRepository.getApiTicket(
                        appId = config.appId, accessToken = accessToken, userId = userId
                    )

                    when (apiTicketResult) {
                        is ApiResult.Success -> {
                            val tickets = apiTicketResult.data.tickets
                            if (tickets.isNullOrEmpty()) {
                                callback.onInitFailed(null)
                                return
                            }

                            val apiTicket = tickets.first().value

                            // 3. 生成签名并开始验证
                            val params = generateFaceVerifyParams(
                                config = config,
                                faceId = faceId,
                                orderNo = orderNo,
                                userId = userId,
                                apiTicket = apiTicket
                            )

                            startVerificationInternal(params, callback)
                        }

                        is ApiResult.Exception -> callback.onInitFailed(null)
                        is ApiResult.Failure -> callback.onInitFailed(null)
                    }
                }

                is ApiResult.Exception -> callback.onInitFailed(null)
                is ApiResult.Failure -> callback.onInitFailed(null)
            }
        } catch (_: Exception) {
            callback.onInitFailed(null)
        }
    }

    /**
     * 内部验证方法
     */
    private fun startVerificationInternal(
        params: FaceVerifyParams, callback: FaceVerifyCallback
    ) {
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

        val verifySdk = WbCloudFaceVerifySdk.getInstance()
        val data = bundleOf(WbCloudFaceContant.INPUT_DATA to inputData)

        // 初始化SDK
        verifySdk.initWillSdk(context, data, object : WbCloudFaceVerifyLoginListener {
            override fun onLoginSuccess() {
                callback.onInitSuccess()
                // 初始化成功后开始人脸验证
                startVerification(verifySdk, callback)
            }

            override fun onLoginFailed(error: WbFaceError?) {
                callback.onInitFailed(error)
            }
        })
    }

    /**
     * 开始验证流程
     */
    private fun startVerification(
        sdk: WbCloudFaceVerifySdk, callback: FaceVerifyCallback
    ) {
        sdk.startWbFaceVerifySdk(context) { result ->
            if (result.isSuccess) {
                callback.onVerifySuccess(result)
            } else {
                callback.onVerifyFailed(result.error)
            }
        }
    }

    /**
     * 生成人脸验证参数
     */
    private fun generateFaceVerifyParams(
        config: TencentCloudConfig,
        faceId: String,
        orderNo: String,
        userId: String,
        apiTicket: String
    ): FaceVerifyParams {
        // 生成32位随机字符串（字母和数字）
        val nonce = generateNonce()

        // 生成签名（这里需要根据腾讯云文档实现具体的签名算法）
        val sign = generateSign(
            appId = config.appId,
            orderNo = orderNo,
            nonce = nonce,
            userId = userId,
            apiTicket = apiTicket
        )

        return FaceVerifyParams(
            faceId = faceId,
            orderNo = orderNo,
            appId = config.appId,
            nonce = nonce,
            userId = userId,
            sign = sign,
            keyLicence = BuildConfig.TX_Licence
        )
    }

    /**
     * 生成签名
     * 根据腾讯云官方文档实现签名算法：
     * 1. 将 appId、userId、version、ticket、nonce 五个参数按字典序排序
     * 2. 拼接成字符串
     * 3. 进行 SHA1 编码
     */
    private fun generateSign(
        appId: String, orderNo: String, nonce: String, userId: String, apiTicket: String
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
     * 生成32位随机字符串（字母和数字）
     * 符合腾讯云文档要求
     */
    private fun generateNonce(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }
    
    /**
     * SHA1 编码
     */
    private fun sha1(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 释放SDK资源
     */
    fun release() {
        try {
            WbCloudFaceVerifySdk.getInstance().release()
        } catch (_: Exception) {
            // 忽略释放异常
        }
    }
}