package com.ytone.longcare.api.request

import com.squareup.moshi.JsonClass

/**
 * 保存文件请求参数
 */
@JsonClass(generateAdapter = true)
data class SaveFileParamModel(
    /**
     * 文件夹类型 13 服务图片
     */
    val folderType: Int? = null,
    
    /**
     * 完整的filekey;比如:1001/private/serivce/xxxxx.png
     */
    val fileKey: String? = null,
    
    /**
     * 文件大小 单位(byte)
     */
    val fileSize: Long? = null
)