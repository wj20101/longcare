package com.ytone.longcare.common.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider 统一管理工具类
 * 避免在代码中硬编码包名和authorities
 */
object FileProviderHelper {
    
    /**
     * FileProvider的authorities
     */
    private fun getAuthorities(context: Context): String {
        return "${context.packageName}.fileprovider"
    }
    
    /**
     * 为文件创建Uri
     * @param context 上下文
     * @param file 文件
     * @return 文件的Uri
     */
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            getAuthorities(context),
            file
        )
    }
}
