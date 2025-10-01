package com.ytone.longcare.features.photoupload.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 水印数据模型
 *
 * @property title 水印标题 (例如 "服务前", "服务中", "服务后")
 * @property insuredPerson 受保人信息 (例如 "参保人员:张三")
 * @property caregiver 护理员信息 (例如 "护理人员:李四")
 * @property time 拍摄时间 (格式 "yyyy-MM-dd HH:mm:ss")
 * @property location 定位信息 (例如 "卫星定位:116.12345,39.54321")
 * @property address 拍摄地址 (例如 "拍摄地址:北京市朝阳区xx路xx号")
 */
@Keep
@Serializable
data class WatermarkData(
    val title: String,
    val insuredPerson: String,
    val caregiver: String,
    val time: String,
    val location: String,
    val address: String
)
