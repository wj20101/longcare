package com.ytone.longcare.common.utils

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

/**
 * 存储空间工具
 * 优先返回系统可分配空间（含可回收缓存），在低版本或异常时回退到usableSpace。
 */
object StorageSpaceUtils {

    fun getAllocatableBytes(context: Context, path: File): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return path.usableSpace
        }

        val storageManager = context.getSystemService(StorageManager::class.java)
            ?: return path.usableSpace

        return runCatching {
            val uuid = storageManager.getUuidForPath(path)
            storageManager.getAllocatableBytes(uuid)
        }.getOrElse {
            path.usableSpace
        }
    }
}
