package com.ytone.longcare.common.provider

import androidx.core.content.FileProvider

/**
 * 自定义FileProvider类
 * 避免与其他SDK的FileProvider产生冲突
 */
class CustomFileProvider : FileProvider() {
    // 继承自androidx.core.content.FileProvider
    // 无需额外实现，只是为了避免命名冲突
}