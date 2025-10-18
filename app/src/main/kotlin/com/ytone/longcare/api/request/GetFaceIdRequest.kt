package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.ytone.longcare.common.utils.FaceVerificationManager.Companion.FACE_VERSION

/**
 * 获取faceId的请求体
 * 根据腾讯云官方文档：https://cloud.tencent.com/document/product/1007/35866
 */
@JsonClass(generateAdapter = true)
data class GetFaceIdRequest(
    /**
     * 业务流程唯一标识，即 WBappid
     */
    @param:Json(name = "appId")
    val appId: String,

    /**
     * 订单号，字母/数字组成的字符串，由合作方上传，每次唯一，不能超过32位
     */
    @param:Json(name = "orderNo")
    val orderNo: String,

    /**
     * 姓名
     */
    @param:Json(name = "name")
    val name: String,

    /**
     * 证件号码
     */
    @param:Json(name = "idNo")
    val idNo: String,

    /**
     * 用户 ID ，用户的唯一标识（不能带有特殊字符），需要跟生成签名的 userId 保持一致。同一个用户的 userId 请保持一致，不同用户请不要使用同一个userId，我们会根据 userId 来做防重复点击优化以及登录态校验
     */
    @param:Json(name = "userId")
    val userId: String,

    /**
     * 版本号，默认参数值为：1.0.0
     */
    @param:Json(name = "version")
    val version: String = FACE_VERSION,

    /**
     * 签名：使用上面生成的签名
     */
    @param:Json(name = "sign")
    val sign: String,

    /**
     * 随机数
     */
    @param:Json(name = "nonce")
    val nonce: String,

    /**
     * 比对源照片，注意：原始图片不能超过500k，且必须为 JPG 或 PNG、BMP 格式。
     * 参数有值：使用合作伙伴提供的比对源照片进行比对，必须注意是正脸可信照片，照片质量由合作方保证
     * 参数为空 ：根据身份证号 + 姓名使用权威数据源比对
     */
    @param:Json(name = "sourcePhotoStr")
    val sourcePhotoStr: String? = null,

    /**
     * 比对源照片类型
     * 参数值为1 时是：水纹正脸照
     * 参数值为 2 时是：高清正脸照
     * 重要提示：照片上无水波纹的为高清照，请勿传错，否则影响比对准确率。
     * 如有疑问，请联系腾讯云技术支持线下确认
     */
    @param:Json(name = "sourcePhotoType")
    val sourcePhotoType: String? = null
)